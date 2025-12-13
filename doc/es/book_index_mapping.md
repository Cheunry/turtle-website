PUT /book
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "workDirection": { "type": "byte" },
      "categoryId": { "type": "integer" },
      "categoryName": { "type": "keyword" },
      "picUrl": { "type": "keyword", "index": false },
      "bookName": { "type": "text", "analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
      "authorName": { "type": "text", "analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
      "bookDesc": { "type": "text", "analyzer": "ik_max_word" },
      "score": { "type": "integer" },
      "bookStatus": { "type": "byte" },
      "visitCount": { "type": "long" },
      "wordCount": { "type": "integer" },
      "commentCount": { "type": "integer" },
      "lastChapterName": { "type": "text", "analyzer": "ik_smart", "index": false },
      "lastChapterUpdateTime": { "type": "date", "format": "epoch_millis" },
      "isVip": { "type": "byte" }
    }
  }
}

1.查询数量： GET book/_count
执行结果：
{
    "count": 110,
    "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
    }
}
2.删除索引 ：DELETE book