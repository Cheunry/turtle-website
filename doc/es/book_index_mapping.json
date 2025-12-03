{
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