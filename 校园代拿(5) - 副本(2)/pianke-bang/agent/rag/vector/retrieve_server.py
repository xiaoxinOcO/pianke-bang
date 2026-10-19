"""片刻帮向量检索服务：只做相似度检索，生成仍由 Java / DeepSeek 负责。"""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from langchain_chroma import Chroma
from langchain_openai import OpenAIEmbeddings
from pydantic import BaseModel, Field

load_dotenv()

ROOT = Path(__file__).resolve().parent
CHROMA_DIR = ROOT / "chroma_db"
COLLECTION_NAME = "campus_knowledge"

app = FastAPI(title="片刻帮向量检索", version="1.0.0")


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    # 便于排查 Java 侧 422：打印 body / 校验细节
    body = await request.body()
    print(f"[422] path={request.url.path} body={body!r} errors={exc.errors()}")
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


class RetrieveRequest(BaseModel):
    query: str = Field(min_length=1, max_length=500)
    top_k: int = Field(default=3, ge=1, le=10)


class HitItem(BaseModel):
    id: str
    title: str
    tags: str = ""
    source: str
    content: str
    score: float


class RetrieveResponse(BaseModel):
    hits: list[HitItem]
    backend: str = "chroma"


def _resolve_dashscope_base_url() -> str:
    base_url = (os.getenv("DASHSCOPE_BASE_URL") or "").strip()
    if not base_url or "{WorkspaceId}" in base_url:
        return "https://dashscope.aliyuncs.com/compatible-mode/v1"
    return base_url.rstrip("/")


@lru_cache(maxsize=1)
def get_vectorstore() -> Chroma:
    api_key = os.getenv("DASHSCOPE_API_KEY")
    if not api_key:
        raise RuntimeError("缺少 DASHSCOPE_API_KEY")
    if not CHROMA_DIR.exists():
        raise RuntimeError("未找到 chroma_db，请先运行: python ingest_knowledge.py")

    embeddings = OpenAIEmbeddings(
        model=os.getenv("DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v3"),
        api_key=api_key,
        base_url=_resolve_dashscope_base_url(),
        check_embedding_ctx_length=False,
    )
    return Chroma(
        collection_name=COLLECTION_NAME,
        embedding_function=embeddings,
        persist_directory=str(CHROMA_DIR),
    )


@app.get("/health")
def health() -> dict:
    ready = CHROMA_DIR.exists()
    return {
        "ok": True,
        "chroma_ready": ready,
        "collection": COLLECTION_NAME,
    }


@app.post("/api/retrieve", response_model=RetrieveResponse)
def retrieve(body: RetrieveRequest) -> RetrieveResponse:
    query = body.query.strip()
    if not query:
        raise HTTPException(status_code=400, detail="query 不能为空")

    try:
        vectorstore = get_vectorstore()
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    try:
        pairs = vectorstore.similarity_search_with_relevance_scores(query, k=body.top_k)
    except Exception:
        # 部分版本只有 with_score（距离，越小越相似）
        scored = vectorstore.similarity_search_with_score(query, k=body.top_k)
        pairs = []
        for doc, distance in scored:
            # 粗略转成 0~1 相关度
            relevance = 1.0 / (1.0 + float(distance))
            pairs.append((doc, relevance))

    hits: list[HitItem] = []
    for doc, relevance in pairs:
        meta = doc.metadata or {}
        source = str(meta.get("source") or meta.get("id") or "unknown")
        doc_id = str(meta.get("id") or source.replace(".md", ""))
        title = str(meta.get("title") or doc_id)
        tags = str(meta.get("tags") or "")
        # 映射到 Java 关键词分数量级（AdminAiService 用 >= 5 作为有效命中）
        mapped = 5.0 + max(0.0, min(1.0, float(relevance))) * 10.0
        hits.append(
            HitItem(
                id=doc_id,
                title=title,
                tags=tags,
                source=source,
                content=doc.page_content,
                score=round(mapped, 4),
            )
        )

    return RetrieveResponse(hits=hits)


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("RAG_VECTOR_HOST", "127.0.0.1")
    port = int(os.getenv("RAG_VECTOR_PORT", "8765"))
    uvicorn.run(app, host=host, port=port, reload=False)
