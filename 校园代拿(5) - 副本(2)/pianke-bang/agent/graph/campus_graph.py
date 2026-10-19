"""片刻帮客服 LangGraph：route → tools → retrieve → generate。"""

from __future__ import annotations

import json
import os
import re
from typing import Any, TypedDict

import httpx
from langchain_openai import ChatOpenAI
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, Field


class AgentState(TypedDict, total=False):
    message: str
    username: str
    user_id: int | None
    history: list[dict[str, str]]
    normalized: str
    tool_names: list[str]
    need_rag: bool
    tool_results: list[dict[str, Any]]
    rag_hits: list[dict[str, Any]]
    reply: str
    tool_payload: dict[str, Any] | None
    citations: list[str]
    actions: list[str]
    trace: list[str]
    model: str
    source: str


def _normalize(text: str) -> str:
    if not text:
        return ""
    t = text.lower().strip()
    t = re.sub(r"[\s　]+", "", t)
    t = re.sub(r"[？?。！!，,、；;：:\-—_（）()【】\[\]《》\"'‘’“”]", "", t)
    return t


def _is_personal_withdraw_howto(n: str) -> bool:
    about_money = any(k in n for k in ("钱", "钱包", "余额", "提现"))
    howto = any(k in n for k in ("怎么", "如何", "怎样", "拿出来", "取出来", "提取", "规则"))
    personal = any(k in n for k in ("我", "钱包", "余额"))
    return about_money and howto and personal


def _looks_like_credit(n: str) -> bool:
    """个人信誉分查询（含短问与「信用积分」别名）；规则 FAQ 不走工具。"""
    if any(
        k in n
        for k in (
            "信誉分规则",
            "超时扣多少",
            "怎么恢复信誉",
            "如何恢复信誉",
            "怎么扣信誉",
            "如何扣分",
        )
    ):
        return False
    if n in (
        "信誉",
        "信誉分",
        "信用分",
        "信用积分",
        "我的信誉",
        "我的信誉分",
        "我的信用分",
        "我的信用积分",
    ):
        return True
    if any(k in n for k in ("信誉分", "信用积分", "信用分")):
        return True
    if any(k in n for k in ("接不了单", "不能接单", "禁止接单", "被禁接", "为什么接不了")):
        return True
    if "信誉" in n and any(k in n for k in ("多少", "我的", "查询", "查一下", "几分", "看看", "查下")):
        return True
    if "信用" in n and any(k in n for k in ("积分", "多少", "我的", "分数")):
        return True
    return False


def _is_policy_howto(n: str) -> bool:
    """规则/能否类问题：只走知识库，不要误调 list_my_tasks。"""
    if any(
        k in n
        for k in (
            "可以取消",
            "能不能取消",
            "能否取消",
            "怎么取消",
            "如何取消",
            "怎样取消",
            "取消已接",
            "取消规则",
            "怎么发布",
            "如何发布",
            "怎么接单",
            "如何接单",
            "怎么提现",
            "如何提现",
            "手续费吗",
            "酬劳什么时候",
            "如何成为接单员",
            "怎么申请接单员",
        )
    ):
        return True
    if "取消" in n and any(k in n for k in ("可以", "能不能", "能否", "怎么", "如何", "吗", "规则")):
        return True
    return False


def _rule_personal_tools(n: str) -> list[str]:
    """确定性个人数据路由，避免 LLM 漏选短问。"""
    if _is_policy_howto(n):
        return []
    tools: list[str] = []
    if _looks_like_credit(n):
        tools.append("get_credit")
    if any(k in n for k in ("我的钱包", "钱包余额", "查余额", "余额多少", "还有多少钱")) or n in ("余额", "钱包"):
        tools.append("get_wallet")
    if any(k in n for k in ("我的任务", "我的订单", "查任务", "任务列表", "历史任务", "待接单", "配送中")):
        tools.append("list_my_tasks")
    # 去重保序
    seen: set[str] = set()
    out: list[str] = []
    for t in tools:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out[:3]


def _llm() -> ChatOpenAI:
    api_key = (os.getenv("DEEPSEEK_API_KEY") or "").strip()
    if not api_key:
        raise RuntimeError("缺少 DEEPSEEK_API_KEY")
    base = (os.getenv("DEEPSEEK_BASE_URL") or "https://api.deepseek.com").rstrip("/")
    model = os.getenv("DEEPSEEK_MODEL") or "deepseek-chat"
    return ChatOpenAI(
        model=model,
        api_key=api_key,
        base_url=base,
        temperature=0.2,
        timeout=90,
    )


class RouteDecision(BaseModel):
    tools: list[str] = Field(default_factory=list, description="要调用的工具名")
    need_rag: bool = Field(default=False, description="是否需要知识库")


def route_node(state: AgentState) -> dict[str, Any]:
    msg = state.get("message") or ""
    n = _normalize(msg)
    trace = list(state.get("trace") or [])
    tools: list[str] = []
    need_rag = False

    rule_tools = _rule_personal_tools(n)
    if _is_policy_howto(n):
        tools = []
        need_rag = True
        trace.append("route:rule=policy_howto")
    elif rule_tools:
        tools = rule_tools
        need_rag = False
        trace.append(f"route:rule=personal_tools:{tools}")
    elif _is_personal_withdraw_howto(n):
        tools = ["get_wallet", "get_rider_cert"]
        need_rag = True
        trace.append("route:rule=personal_withdraw")
    else:
        try:
            llm = _llm()
            prompt = (
                "你是片刻帮客服工具路由器。根据用户问题输出要调用的工具名列表，以及是否需要知识库。\n"
                "可选工具：get_wallet,list_my_tasks,get_task_detail,get_task_timeline,"
                "list_withdrawals,get_credit,get_rider_cert,list_appeals,list_coupons,"
                "get_urgent_quota,list_transactions\n"
                "规则：查个人数据必须选工具；问怎么做/规则/可不可以设 need_rag=true 且不要选 list_my_tasks。\n"
                "短问示例：「信誉分」「信用积分」「我的钱包」必须选对应工具。\n"
                "「可以取消已接的任务吗」只查知识库，不要 list_my_tasks。\n"
                f"用户问题：{msg}"
            )
            structured = llm.with_structured_output(RouteDecision)
            decision: RouteDecision = structured.invoke(prompt)
            tools = [t for t in (decision.tools or []) if t][:3]
            need_rag = bool(decision.need_rag)
            trace.append(f"route:llm tools={tools} need_rag={need_rag}")
        except Exception as ex:
            # 路由失败时保守：有「我的」则试钱包，howto 开 rag
            if any(k in n for k in ("钱包", "余额", "多少钱")):
                tools = ["get_wallet"]
            if any(k in n for k in ("任务", "订单")):
                tools = (tools + ["list_my_tasks"])[:3]
            if _looks_like_credit(n):
                tools = (["get_credit"] + tools)[:3]
            if any(k in n for k in ("怎么", "如何", "规则", "提现", "发布")):
                need_rag = True
            trace.append(f"route:fallback err={ex}")

    if any(k in n for k in ("怎么", "如何", "规则", "流程")):
        need_rag = True

    return {
        "normalized": n,
        "tool_names": tools,
        "need_rag": need_rag,
        "trace": trace,
    }


def tools_node(state: AgentState) -> dict[str, Any]:
    names = list(state.get("tool_names") or [])
    trace = list(state.get("trace") or [])
    if not names:
        trace.append("tools:skip")
        return {"tool_results": [], "trace": trace}

    url = (os.getenv("JAVA_TOOLS_URL") or "http://127.0.0.1:8104/internal/support/run-tools").strip()
    token = (os.getenv("CAMPUS_AGENT_GRAPH_TOKEN") or "").strip()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Campus-Agent-Token"] = token

    payload = {
        "username": state.get("username") or "",
        "userId": state.get("user_id"),
        "normalized": state.get("normalized") or "",
        "tools": [{"name": n, "arguments": {}} for n in names],
    }
    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(url, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
        results = data.get("results") or []
        trace.append(f"tools:ok n={len(results)}")
        return {"tool_results": results, "trace": trace}
    except Exception as ex:
        trace.append(f"tools:err {ex}")
        return {"tool_results": [], "trace": trace}


def retrieve_node(state: AgentState) -> dict[str, Any]:
    trace = list(state.get("trace") or [])
    if not state.get("need_rag"):
        trace.append("retrieve:skip")
        return {"rag_hits": [], "trace": trace}

    base = (os.getenv("RAG_VECTOR_URL") or "http://127.0.0.1:8765").rstrip("/")
    query = state.get("message") or ""
    n = state.get("normalized") or ""
    if _is_personal_withdraw_howto(n) or "提现" in n or "拿出来" in n:
        query = f"{query} 提现 钱包 余额 手续费"

    try:
        with httpx.Client(timeout=20.0) as client:
            resp = client.post(f"{base}/api/retrieve", json={"query": query, "top_k": 3})
            resp.raise_for_status()
            data = resp.json()
        hits = data.get("hits") or []
        trace.append(f"retrieve:ok n={len(hits)}")
        return {"rag_hits": hits, "trace": trace}
    except Exception as ex:
        trace.append(f"retrieve:err {ex}")
        return {"rag_hits": [], "trace": trace}


def generate_node(state: AgentState) -> dict[str, Any]:
    trace = list(state.get("trace") or [])
    tool_results = list(state.get("tool_results") or [])
    rag_hits = list(state.get("rag_hits") or [])
    message = state.get("message") or ""

    # 无工具无检索：短拒答 / 引导
    if not tool_results and not rag_hits:
        reply = (
            "这个问题我这边暂时没有足够信息。"
            "你可以问我：我的钱包、我的任务、怎么提现、如何发布任务；"
            "复杂问题也可转人工。"
        )
        trace.append("generate:empty")
        return {
            "reply": reply,
            "tool_payload": None,
            "citations": [],
            "actions": ["suggest_handoff"],
            "model": "langgraph-empty",
            "source": "langgraph",
            "trace": trace,
        }

    # 仅工具且无 RAG：直接拼工具回复（快路径）
    if tool_results and not rag_hits and not state.get("need_rag"):
        parts = []
        payload = None
        actions: list[str] = []
        for r in tool_results:
            if r.get("reply"):
                parts.append(str(r["reply"]).strip())
            if payload is None and r.get("toolPayload"):
                payload = r.get("toolPayload")
            for a in r.get("actions") or []:
                if a and a not in actions:
                    actions.append(str(a))
        reply = "\n".join(p for p in parts if p) or "已查询到相关信息。"
        trace.append("generate:tools_only")
        return {
            "reply": reply,
            "tool_payload": payload,
            "citations": [],
            "actions": actions,
            "model": "langgraph-tools",
            "source": "langgraph",
            "trace": trace,
        }

    # 综合生成
    tool_ctx = []
    payload = None
    actions: list[str] = []
    for i, r in enumerate(tool_results, 1):
        tool_ctx.append(f"### 工具结果{i}\nsource={r.get('source')}\nreply={r.get('reply')}")
        if r.get("toolPayload") is not None:
            tool_ctx.append("payload=" + json.dumps(r.get("toolPayload"), ensure_ascii=False))
            if payload is None:
                payload = r.get("toolPayload")
        for a in r.get("actions") or []:
            if a and str(a) not in actions:
                actions.append(str(a))

    rag_parts = []
    citations: list[str] = []
    for h in rag_hits:
        title = h.get("title") or h.get("id") or ""
        content = h.get("content") or ""
        if title:
            citations.append(str(title))
        rag_parts.append(f"### {title}\n{content}")

    system = (
        "你是片刻帮校园互助平台的智能客服「片刻小新」。"
        "综合【工具查询结果】（真实用户数据）与【检索到的知识】简洁回答。"
        "数据以工具为准，规则以知识为准；不要编造；2～6 句中文。"
        "需要转人工时在末尾单独一行输出 [[ACTION:suggest_handoff]]。"
    )
    user_content = (
        "【工具查询结果】\n"
        + ("\n".join(tool_ctx) if tool_ctx else "（无）")
        + "\n\n【检索到的知识】\n"
        + ("\n\n".join(rag_parts) if rag_parts else "（无）")
        + "\n\n【用户问题】\n"
        + message
    )

    try:
        llm = _llm()
        history = state.get("history") or []
        msgs: list[dict[str, str]] = [{"role": "system", "content": system}]
        for item in history[-6:]:
            role = (item.get("role") or "").strip().lower()
            content = (item.get("content") or "").strip()
            if role in ("user", "assistant") and content:
                msgs.append({"role": role, "content": content[:500]})
        msgs.append({"role": "user", "content": user_content})
        out = llm.invoke(msgs)
        raw = (out.content or "").strip() if out else ""
        reply, more_actions = _strip_actions(raw)
        for a in more_actions:
            if a not in actions:
                actions.append(a)
        if not reply:
            reply = "\n".join(str(r.get("reply") or "") for r in tool_results).strip() or "已查询到相关信息。"
        trace.append("generate:llm_ok")
        return {
            "reply": reply,
            "tool_payload": payload,
            "citations": citations,
            "actions": actions,
            "model": os.getenv("DEEPSEEK_MODEL") or "deepseek-chat",
            "source": "langgraph",
            "trace": trace,
        }
    except Exception as ex:
        trace.append(f"generate:err {ex}")
        fallback = "\n".join(str(r.get("reply") or "") for r in tool_results).strip()
        if not fallback and rag_hits:
            fallback = "根据知识库，建议你到「我的钱包」查看提现入口；未认证接单员需先完成接单员申请。"
        return {
            "reply": fallback or "智能客服暂时繁忙，请稍后再试或转人工。",
            "tool_payload": payload,
            "citations": citations,
            "actions": actions or ["suggest_handoff"],
            "model": "langgraph-fallback",
            "source": "langgraph",
            "trace": trace,
        }


def _strip_actions(text: str) -> tuple[str, list[str]]:
    actions: list[str] = []
    if not text:
        return "", actions
    lines = []
    for line in text.splitlines():
        m = re.match(r"\s*\[\[ACTION:([^\]]+)\]\]\s*", line.strip())
        if m:
            actions.append(m.group(1).strip())
        else:
            lines.append(line)
    return "\n".join(lines).strip(), actions


def build_graph():
    g = StateGraph(AgentState)
    g.add_node("route", route_node)
    g.add_node("tools", tools_node)
    g.add_node("retrieve", retrieve_node)
    g.add_node("generate", generate_node)
    g.add_edge(START, "route")
    g.add_edge("route", "tools")
    g.add_edge("tools", "retrieve")
    g.add_edge("retrieve", "generate")
    g.add_edge("generate", END)
    return g.compile()


GRAPH = build_graph()


def run_agent(
    message: str,
    username: str,
    user_id: int | None = None,
    history: list[dict[str, str]] | None = None,
) -> dict[str, Any]:
    init: AgentState = {
        "message": message,
        "username": username,
        "user_id": user_id,
        "history": history or [],
        "trace": [],
    }
    final = GRAPH.invoke(init)
    return {
        "reply": final.get("reply") or "",
        "model": final.get("model") or "langgraph",
        "source": final.get("source") or "langgraph",
        "actions": final.get("actions") or [],
        "citations": final.get("citations") or [],
        "toolPayload": final.get("tool_payload"),
        "trace": final.get("trace") or [],
    }
