#!/usr/bin/env python3
"""
批量向 book_info / book_chapter 插入测试数据（直连 MySQL）。

依赖: pip install pymysql

用法示例:
  export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root \\
    MYSQL_PASSWORD=xxx MYSQL_DATABASE=turtle_website
  python3 doc/sql/seed_bulk_books_chapters.py

环境变量均可选，缺省为上面常见本地配置。执行前请确认库名与账号。

说明:
  - book_info.id、book_chapter.id 显式指定，避免与自增序列错位。
  - score 字段按表注释「真实评分 = score/10」，若要展示 6.0 星则存 60；若你更希望存字面 6，把 SCORE 改为 6。
  - 执行结束会尝试把两张表的 AUTO_INCREMENT 调到 MAX(id)+1，减少后续自增冲突。
"""

from __future__ import annotations

import os
import random
import sys
from datetime import datetime

try:
    import pymysql
except ImportError:
    print("请先安装: pip install pymysql", file=sys.stderr)
    sys.exit(1)

# ---------- 你的业务参数 ----------
BOOK_COUNT = 1000
CHAPTERS_PER_BOOK = 20
CHAPTER_WORD_MIN = 150
CHAPTER_WORD_MAX = 300

BOOK_ID_START = 1431630596354977810
CHAPTER_ID_START = 1445988184596992065

# 展示为 6.0 / 10 星（score/10）；若需库存 6 请改为 6
SCORE = 60

VISIT_COUNT = 103
AUTHOR_ID = 0
PIC_URL = (
    "https://turtle-website-1379089820.cos.ap-beijing.myqcloud.com/"
    "resource/2025/12/22/b1b7e29159423d4f0ab605a35245a3ed.png"
)
FIXED_DT = datetime(2019, 1, 1, 0, 0, 0)

CATEGORIES = {
    1: "玄幻奇幻",
    2: "武侠仙侠",
    3: "都市言情",
    4: "历史军事",
    5: "科幻灵异",
    6: "网游竞技",
    7: "温柔叙事",
}

CONTENT_SEED = (
    "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。寒来暑往，秋收冬藏。"
    "闰余成岁，律吕调阳。云腾致雨，露结为霜。金生丽水，玉出昆冈。"
)

SURNAMES = (
    "慕容",
    "上官",
    "司徒",
    "欧阳",
    "司马",
    "诸葛",
    "南宫",
    "东方",
    "独孤",
    "皇甫",
    "尉迟",
    "令狐",
    "百里",
    "东郭",
    "梁丘",
    "左丘",
    "端木",
    "闻人",
    "公羊",
    "澹台",
)


def env(name: str, default: str) -> str:
    v = os.environ.get(name)
    return v if v is not None and v != "" else default


def connect():
    return pymysql.connect(
        host=env("MYSQL_HOST", "127.0.0.1"),
        port=int(env("MYSQL_PORT", "3306")),
        user=env("MYSQL_USER", "root"),
        password=env("MYSQL_PASSWORD", ""),
        database=env("MYSQL_DATABASE", "turtle_website"),
        charset="utf8mb4",
        autocommit=False,
    )


def gen_content(length: int) -> str:
    buf: list[str] = []
    total = 0
    while total < length:
        buf.append(CONTENT_SEED)
        total += len(CONTENT_SEED)
    s = "".join(buf)[:length]
    return s


def random_author_name(rng: random.Random) -> str:
    return f"{rng.choice(SURNAMES)}{rng.randint(10, 99)}笔名{rng.randint(1000, 9999)}"


def main() -> None:
    rng = random.Random(20260419)

    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM book_info WHERE id >= %s", (BOOK_ID_START,))
            if cur.fetchone()[0] > 0:
                print(
                    f"已存在 id >= {BOOK_ID_START} 的书籍，为避免重复请先清理或改 BOOK_ID_START。",
                    file=sys.stderr,
                )
                sys.exit(2)

        book_sql = """
            INSERT INTO book_info (
                id, work_direction, category_id, category_name,
                pic_url, book_name, author_id, author_name, book_desc,
                score, book_status, visit_count, word_count, comment_count,
                last_chapter_name, last_chapter_update_time, is_vip,
                create_time, update_time, last_chapter_num,
                audit_status
            ) VALUES (
                %s, %s, %s, %s,
                %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s,
                %s, %s, %s,
                %s, %s, %s,
                %s
            )
        """

        chapter_sql = """
            INSERT INTO book_chapter (
                id, book_id, chapter_num, chapter_name, word_count, content, is_vip,
                create_time, update_time, audit_status
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s
            )
        """

        book_batch: list[tuple] = []
        chapter_batch: list[tuple] = []

        chapter_id = CHAPTER_ID_START

        for i in range(BOOK_COUNT):
            book_id = BOOK_ID_START + i
            cat_id = rng.randint(1, 7)
            cat_name = CATEGORIES[cat_id]
            work_dir = rng.randint(0, 1)
            author_name = random_author_name(rng)
            book_name = f"批量种子书·第{i + 1:04d}册"
            book_desc = (
                f"【测试数据】{cat_name}向，作家「{author_name}」，用于本地批量填充。"
                f"书籍ID={book_id}。"
            )

            total_words = 0
            chapters_meta: list[tuple[int, str, int, str]] = []

            for cnum in range(1, CHAPTERS_PER_BOOK + 1):
                wc = rng.randint(CHAPTER_WORD_MIN, CHAPTER_WORD_MAX)
                body = gen_content(wc)
                total_words += wc
                cname = f"第{cnum}章 试读段落"
                chapters_meta.append((cnum, cname, wc, body))

            last_cname = chapters_meta[-1][1]

            book_batch.append(
                (
                    book_id,
                    work_dir,
                    cat_id,
                    cat_name,
                    PIC_URL,
                    book_name,
                    AUTHOR_ID,
                    author_name,
                    book_desc,
                    SCORE,
                    0,
                    VISIT_COUNT,
                    total_words,
                    0,
                    last_cname,
                    FIXED_DT,
                    0,
                    FIXED_DT,
                    FIXED_DT,
                    CHAPTERS_PER_BOOK,
                    1,
                )
            )

            for cnum, cname, wc, body in chapters_meta:
                chapter_batch.append(
                    (
                        chapter_id,
                        book_id,
                        cnum,
                        cname,
                        wc,
                        body,
                        0,
                        FIXED_DT,
                        FIXED_DT,
                        1,
                    )
                )
                chapter_id += 1

        with conn.cursor() as cur:
            print(f"插入 book_info: {len(book_batch)} 行 …")
            cur.executemany(book_sql, book_batch)
            print(f"插入 book_chapter: {len(chapter_batch)} 行 …")
            for start in range(0, len(chapter_batch), 500):
                cur.executemany(chapter_sql, chapter_batch[start : start + 500])

            cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM book_info")
            next_book = cur.fetchone()[0]
            cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM book_chapter")
            next_ch = cur.fetchone()[0]
            cur.execute(f"ALTER TABLE book_info AUTO_INCREMENT = {next_book}")
            cur.execute(f"ALTER TABLE book_chapter AUTO_INCREMENT = {next_ch}")

        conn.commit()
        print("完成。已提交事务并调整 AUTO_INCREMENT。")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
