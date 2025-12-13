package com.novel.author.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.author.dao.entity.AuthorInfo;
import com.novel.author.dao.mapper.AuthorInfoMapper;
import com.novel.author.dto.AuthorInfoDto;
import com.novel.common.auth.JwtUtils;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.config.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Objects;


/**
 * 认证授权拦截器：
 * 为了注入其它的 Spring beans，
 * 需要通过 @Component 注解将该拦截器注册到 Spring 上下文
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthorInfoMapper authorInfoMapper;

    /**
     * handle 执行前调用
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        // 获取登录 JWT
        String token = request.getHeader(SystemConfigConsts.HTTP_AUTH_HEADER_NAME);

        // 开始认证
        if (!StringUtils.hasText(token)) {
            // token 为空
            throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
        }

        Long userId = JwtUtils.parseToken(token, SystemConfigConsts.NOVEL_FRONT_KEY);
        if (Objects.isNull(userId)) {
            // token 解析失败
            throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
        }

        // 作家权限认证
        AuthorInfoDto authorInfo = getAuthorInfoByUserId(userId);

        if (Objects.isNull(authorInfo)) {

            // 作家账号不存在，无权访问作家专区
            throw new BusinessException(ErrorCodeEnum.USER_UN_AUTH);
        }

        // 设置作家ID到当前线程
        UserHolder.setAuthorId(authorInfo.getId());

        // 设置 userId 到当前线程
        UserHolder.setUserId(userId);

        return HandlerInterceptor.super.preHandle(request, response, handler);

    }

    /**
     * 查询作家信息
     */
    public AuthorInfoDto getAuthorInfoByUserId(Long userId) {
        QueryWrapper<AuthorInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        AuthorInfo authorInfo = authorInfoMapper.selectOne(queryWrapper);
        if (Objects.isNull(authorInfo)) {
            return null;
        }
        return AuthorInfoDto.builder()
                .id(authorInfo.getId())
                .penName(authorInfo.getPenName())
                .status(authorInfo.getStatus()).build();
    }

    /**
     * handler 执行后调用，出现异常不调用
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {

        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    /**
     * DispatcherServlet 完全处理完请求后调用，出现异常照常调用
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) throws Exception {

        // 清理当前线程保存的用户数据
        UserHolder.clear();

        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

}


/*

为什么使用它？

当你在代码中使用一些没有明确标记是否允许为空（Nullable）或是否不能为空（NonNull）的参数、返回值或字段时，
IDE（尤其是使用了 JetBrains IntelliJ IDEA 或其他空值分析工具）或某些编译器的 静态代码分析 可能会发出警告。

例如，在 HandlerInterceptor 的方法签名中：

Java
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
// ...
在某些环境下，request, response, handler, ex 这些参数并没有明确的 @Nullable 或 @NonNull 注解。
如果你在重写方法时没有使用这些参数，或者你认为它们在特定情况下不会为空，
但编译器或 IDE 仍然发出警告，提醒你可能存在空指针风险，
此时你就可以使用 @SuppressWarnings("NullableProblems") 来 消除这些干扰性的警告。

 */