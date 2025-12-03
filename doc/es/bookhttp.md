执行网址：http://www.turtle-pie.space:52579/app/dev_tools#/console
执行代码：
PUT /book
{
  "mappings": {
    "properties": {
      "id": {
        "type": "long",
        "store": true,
        "doc_values": true
      },
      "workDirection": {
        "type": "integer"
      },
      "categoryName": {
        "type": "keyword"
      },
      "picUrl": {
        "type": "keyword",
        "index": false
      },
      "bookName": {
        "type": "text",
        "analyzer": "ik_smart",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "authorName": {
        "type": "text",
        "analyzer": "ik_smart",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "bookDesc": {
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "score": {
        "type": "integer"
      },
      "bookStatus": {
        "type": "integer"
      },
      "visitCount": {
        "type": "long"
      },
      "wordCount": {
        "type": "integer"
      },
      "commentCount": {
        "type": "integer"
      },
      "lastChapterName": {
        "type": "text",
        "analyzer": "ik_smart",
        "index": false
      },
      "lastChapterUpdateTime": {
        "type": "date",
        "format": "epoch_millis"
      },
      "isVip": {
        "type": "integer"
      }
    }
  }
}

执行结果：
{
  "acknowledged": true,
  "shards_acknowledged": true,
  "index": "book"
}



PUT /book
{
"mappings": {
"properties": {
"id": { "type": "long" },
"workDirection": { "type": "byte" },     // 保持 byte，防扩展
"categoryId": { "type": "integer" },      // integer 足够
"categoryName": { "type": "keyword" },
"picUrl": { "type": "keyword", "index": false },
"bookName": { "type": "text", "analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
"authorName": { "type": "text", "analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
"bookDesc": { "type": "text", "analyzer": "ik_max_word" },
"score": { "type": "integer" },
"bookStatus": { "type": "byte" },        // 保持 byte，状态可能会变多
"visitCount": { "type": "long" },
"wordCount": { "type": "integer" },
"commentCount": { "type": "integer" },
"lastChapterName": { "type": "text", "analyzer": "ik_smart", "index": false },
"lastChapterUpdateTime": { "type": "date", "format": "epoch_millis" },
"isVip": { "type": "boolean" },          // 改为 boolean，ES会自动把 0/1 转为 false/true
"createTime": { "type": "date", "format": "epoch_millis" }
}
}
}