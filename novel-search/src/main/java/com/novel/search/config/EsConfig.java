package com.novel.search.config;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

public class EsConfig {

    /*
        遇到问题了再配置，默认让 Spring Boot 自动配置去处理 ES 客户端的创建即可。
     */
    /**
     * 解决 ElasticsearchClientConfigurations 修改默认 ObjectMapper 配置的问题
     */
//    @Bean
//    JacksonJsonpMapper jacksonJsonpMapper() {
//        return new JacksonJsonpMapper();
//    }
}
