package com.novel.user.dto.resp;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserLoginRespDto {

    @Schema(description = "用户ID")
    private Long uid;

    @Schema(description = "用户昵称")
    private String nickName;

    @Schema(description = "用户token")
    private String token;
}

/*
 * 用户token是什么？
 * 相当于登录之后服务器发了一张临时通行证，
 * 后续请求只要拿这张证就可以证明我是谁，而不用反复输入密码
 */

/*
 * 用到的几个注解的作用
 * @Data：Lombok注解，自动生成：
 * getter/setter方法
 * toString()方法
 * equals()和hashCode()方法
 *
 * @Builder：Lombok注解，支持建造者模式：
 * 可以这样创建对象：
 * UserLoginRespDto.builder()
 *     .uid(123L)
 *     .nickName("张三")
 *     .token("abc123")
 *     .build();
 *
 * @Schema(description = "用户ID")的作用：
 * 生成API文档：为前端开发者提供清晰的接口说明
 * 提高协作效率：前后端开发基于文档进行协作
 * 自动生成示例：提供测试用的示例数据
 * 类型和约束说明：明确字段的类型、格式、约束条件
 *
 */
