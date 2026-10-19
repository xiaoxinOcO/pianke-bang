"""将 agent/rag/knowledge/*.md 向量化写入本地 Chroma。"""

from __future__ import annotations

import os
import re
from pathlib import Path

from dotenv import load_dotenv
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings

load_dotenv()

ROOT = Path(__file__).resolve().parent
KNOWLEDGE_DIR = ROOT.parent / "knowledge"
CHROMA_DIR = ROOT / "chroma_db"
COLLECTION_NAME = "campus_knowledge"
EXCLUDE_NAMES = {"00-知识库数据范围.md"}
HEADING = re.compile(r"^#\s+(.+)$", re.MULTILINE)
TAG_LINE = re.compile(r"^标签[:：]\s*(.+)$", re.MULTILINE)


def _resolve_base_url() -> str:
    base_url = (os.getenv("DASHSCOPE_BASE_URL") or "").strip()
    if not base_url or "{WorkspaceId}" in base_url:
        fallback = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        print(f"警告: DASHSCOPE_BASE_URL 无效或含占位符，改用 {fallback}")
        return fallback
    return base_url.rstrip("/")


def _parse_meta(text: str, filename: str) -> tuple[str, str, str]:
    doc_id = filename.replace(".md", "")
    title = doc_id
    tags = ""
    m = HEADING.search(text)
    if m:
        title = m.group(1).strip()
    t = TAG_LINE.search(text)
    if t:
        tags = t.group(1).strip()
    return doc_id, title, tags


def load_knowledge_docs() -> list[Document]:
    docs: list[Document] = []
    if not KNOWLEDGE_DIR.is_dir():
        raise SystemExit(f"知识目录不存在: {KNOWLEDGE_DIR}")

    for path in sorted(KNOWLEDGE_DIR.glob("*.md")):
        if path.name in EXCLUDE_NAMES:
            print(f"跳过: {path.name}")
            continue
        text = path.read_text(encoding="utf-8").strip()
        if not text:
            print(f"跳过空文件: {path.name}")
            continue
        doc_id, title, tags = _parse_meta(text, path.name)
        docs.append(
            Document(
                page_content=text,
                metadata={
                    "id": doc_id,
                    "title": title,
                    "tags": tags,
                    "source": path.name,
                    "path": str(path),
                },
            )
        )
        print(f"加载: {path.name} ({len(text)} 字符) title={title}")
    return docs


def main() -> None:
    api_key = os.getenv("DASHSCOPE_API_KEY")
    if not api_key:
        raise SystemExit("请在 agent/rag/vector/.env 中配置 DASHSCOPE_API_KEY")

    docs = load_knowledge_docs()
    if not docs:
        raise SystemExit(f"未找到可入库文档: {KNOWLEDGE_DIR}")

    embeddings = OpenAIEmbeddings(
        model=os.getenv("DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v3"),
        api_key=api_key,
        base_url=_resolve_base_url(),
        check_embedding_ctx_length=False,
    )

    CHROMA_DIR.mkdir(parents=True, exist_ok=True)
    vectorstore = Chroma(
        collection_name=COLLECTION_NAME,
        embedding_function=embeddings,
        persist_directory=str(CHROMA_DIR),
    )
    existing = vectorstore.get()
    ids = existing.get("ids") or []
    if ids:
        vectorstore.delete(ids=ids)
        print(f"已清空旧数据: {len(ids)} 条")

    vectorstore.add_documents(docs)
    print(f"已写入 Chroma: {len(docs)} 条 -> {CHROMA_DIR} / {COLLECTION_NAME}")

    query = "提现需要什么条件"
    hits = vectorstore.similarity_search(query, k=2)
    print(f"\n检索自检「{query}」:")
    for i, doc in enumerate(hits, 1):
        preview = doc.page_content.replace("\n", " ")[:120]
        print(f"  [{i}] {doc.metadata.get('source')}: {preview}...")


if __name__ == "__main__":
    main()
