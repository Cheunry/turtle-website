package com.novel.search.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 执行器配置类
 * ！！！！！注意：目前基于本地开发环境，网络配置使用默认值或空。后期需要修改！！！！！！！
 * 在本地xxl-job暂时不可用
 */
@Configuration
@Slf4j
public class XxlJobConfig {

    // 调度中心地址
    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    // 访问令牌
    @Value("${xxl.job.accessToken}")
    private String accessToken;

    // 执行器 AppName
    @Value("${xxl.job.executor.appname}")
    private String appname;

    // 执行器 IP (使用默认值空字符串，对应YAML中注释或未配置的情况)
    // 注意: 这里的默认值是空字符串 ""，因为 XXL-JOB 执行器在接收到空字符串时会尝试自动获取 IP。
    @Value("${xxl.job.executor.ip:}")
    private String ip;

    // 执行器端口 (使用默认值 -1，对应YAML中注释或未配置的情况)
    // 端口设置为 -1，意味着执行器将使用随机端口。
    @Value("${xxl.job.executor.port:-1}")
    private int port;

    // 执行器日志路径
    @Value("${xxl.job.executor.logpath}")
    private String logPath;

    // 执行器日志保留天数
    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;


    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> XXL-JOB 执行器配置开始初始化...");

        // 1. 创建执行器实例
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();

        // 2. 设置调度中心配置
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAccessToken(accessToken);

        // 3. 设置执行器自身配置
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setIp(ip);         // 使用配置文件中的值或默认的 ""
        xxlJobSpringExecutor.setPort(port);     // 使用配置文件中的值或默认的 -1
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

        log.info(">>>>>>>>>>> XXL-JOB 执行器配置初始化完成。Appname: {}", appname);
        return xxlJobSpringExecutor;
    }
}