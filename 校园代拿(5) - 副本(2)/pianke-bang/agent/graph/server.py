"""片刻帮 LangGraph 客服编排 HTTP 服务。"""

from __future__ import annotations

import os
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from campus_graph import run_agent

load_dotenv()

app = FastAPI(title="片刻帮 LangGraph Agent", version="1.0.0")


class HistoryItem(BaseModel):
    role: str
    content: str


class AgentChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=800)
    username: str = Field(min_length=1, max_length=64)
    userId: int | None = None
    history: list[HistoryItem] = Field(default_factory=list)


def _assert_token(token: str | None) -> None:
    expected = (os.getenv("CAMPUS_AGENT_GRAPH_TOKEN") or "").strip()
    if not expected:
        return
    if not token or token.strip() != expected:
        raise HTTPException(status_code=401, detail="invalid agent token")


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "ok": True,
        "service": "campus-langgraph",
        "has_deepseek": bool((os.getenv("DEEPSEEK_API_KEY") or "").strip()),
        "java_tools": os.getenv("JAVA_TOOLS_URL") or "http://127.0.0.1:8104/internal/support/run-tools",
        "rag_vector": os.getenv("RAG_VECTOR_URL") or "http://127.0.0.1:8765",
    }


@app.post("/api/agent/chat")
def agent_chat(
    body: AgentChatRequest,
    x_campus_agent_token: str | None = Header(default=None, alias="X-Campus-Agent-Token"),
) -> dict[str, Any]:
    _assert_token(x_campus_agent_token)
    history = [{"role": h.role, "content": h.content} for h in body.history]
    try:
        return run_agent(
            message=body.message.strip(),
            username=body.username.strip(),
            user_id=body.userId,
            history=history,
        )
    except Exception as ex:
        raise HTTPException(status_code=500, detail=str(ex)) from ex


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("AGENT_GRAPH_HOST", "127.0.0.1")
    port = int(os.getenv("AGENT_GRAPH_PORT", "8766"))
    uvicorn.run(app, host=host, port=port, reload=False)
