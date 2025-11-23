package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.common.auth.JwtUtils;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.resp.RestResp;
import com.novel.config.exception.BusinessException;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.resp.UserLoginRespDto;
import com.novel.user.manager.cache.UserCacheManager;
import com.novel.user.manager.redis.RedisKeyConstants;
import com.novel.user.service.UserLoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserLoginServiceImpl implements UserLoginService {

    private final UserInfoMapper userInfoMapper;

    private final RedisTemplate<String, Object> redisTemplate;


    public UserLoginServiceImpl(UserInfoMapper userInfoMapper,
                                @Qualifier("turtleRedisTemplate") RedisTemplate<String, Object> redisTemplate, UserCacheManager userCacheManager){
        this.userInfoMapper = userInfoMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RestResp<UserLoginRespDto> login(UserLoginReqDto dto) {
        String username = dto.getUsername();

        // 检查用户是否被锁定
        if (isUserLocked(username)) {
            long remainTime = getLockRemainTime(username);
            // 使用自定义错误消息，但不传递给BusinessException构造函数
            String errorMessage = String.format("账号已被锁定，请%d分钟后再试", remainTime);
            log.warn(errorMessage);
            throw new BusinessException(ErrorCodeEnum.USER_LOGIN_LIMIT);
        }

        // 查询用户信息
        UserInfo userInfo = findByUsername(dto.getUsername());
        if (Objects.isNull(userInfo)) {
            // 用户不存在，记录失败次数
            processLoginFailure(username);
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }

        // 验证密码
        String encryptedPassword = DigestUtils.md5DigestAsHex(
                dto.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!Objects.equals(userInfo.getPassword(), encryptedPassword)) {
            // 密码错误，记录失败次数
            processLoginFailure(username);
            int remainAttempts = getRemainAttempts(username);
            // 将详细消息记录到日志，但不传递给异常构造函数
            log.warn("用户 {} 密码错误，剩余 {} 次尝试机会", username, remainAttempts);
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);
        }

        // 登录成功，清除失败记录并生成token
        clearLoginFailureRecord(username);
        return buildLoginSuccessResponse(userInfo);
    }


    /**
     * 根据用户名查找用户
     */
    private UserInfo findByUsername(String username) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserInfoTable.COLUMN_USERNAME, username)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return userInfoMapper.selectOne(queryWrapper);
    }

    /**
     * 检查用户是否被锁定
     */
    private boolean isUserLocked(String username) {
        String lockKey = String.format(RedisKeyConstants.USER_LOCK_STATUS, username);
        return redisTemplate.hasKey(lockKey);
    }

    /**
     * 获取锁定剩余时间，单位是分钟
     */
    private long getLockRemainTime(String username) {
        String lockKey = String.format(RedisKeyConstants.USER_LOCK_STATUS, username);
        return redisTemplate.getExpire(lockKey, TimeUnit.MINUTES);
    }

    /**
     * 处理登录失败
     * @param username
     */
    private void processLoginFailure(String username) {
        String failCountKey = String.format(RedisKeyConstants.LOGIN_FAIL_COUNT, username);

        // 增加失败次数
        Long failCount = redisTemplate.opsForValue().increment(failCountKey);
        if (failCount == 1) {
            // 第一次失败，设置过期时间
            redisTemplate.expire(failCountKey, RedisKeyConstants.LOCK_EXPIRE_TIME, TimeUnit.SECONDS);
        }

        // 检查是否达到锁定阈值
        if (failCount >= 5) {
            lockUser(username);
        }
    }

    /**
     * 锁定用户
     */
    private void lockUser(String username) {
        String lockKey = String.format(RedisKeyConstants.USER_LOCK_STATUS, username);
        redisTemplate.opsForValue().set(lockKey, "LOCKED",
                RedisKeyConstants.LOCK_EXPIRE_TIME, TimeUnit.SECONDS);

        // 清除失败计数
        String failCountKey = String.format(RedisKeyConstants.LOGIN_FAIL_COUNT, username);
        redisTemplate.delete(failCountKey);

        log.warn("用户 {} 因连续登录失败被锁定", username);
    }

    /**
     * 获取剩余尝试次数
     * @param username 用户名
     * @return 剩余尝试次数
     */
    private int getRemainAttempts(String username) {

        String failCountKey = String.format(RedisKeyConstants.LOGIN_FAIL_COUNT, username);
        Long failCount = (Long) redisTemplate.opsForValue().get(failCountKey);
        int currentCount = failCount != null ? failCount.intValue() : 0;
        return Math.max(0, 5 - currentCount - 1);
    }

    /**
     * 清除登录失败记录
     * @param username
     */
    private void clearLoginFailureRecord(String username) {

        String failCountKey = String.format(RedisKeyConstants.LOGIN_FAIL_COUNT, username);
        String lockKey = String.format(RedisKeyConstants.USER_LOCK_STATUS, username);

        redisTemplate.delete(failCountKey);
        redisTemplate.delete(lockKey);
    }

    /**
     * 构建登录成功响应
     */
    private RestResp<UserLoginRespDto> buildLoginSuccessResponse(UserInfo userInfo) {

        String token = JwtUtils.generateToken(userInfo.getId(), SystemConfigConsts.NOVEL_FRONT_KEY);
        UserLoginRespDto respDto = UserLoginRespDto.builder()
                .token(token)
                .uid(userInfo.getId())
                .nickName(userInfo.getNickName())
                .build();

        return RestResp.ok(respDto);
    }

    /**
     * 手动解锁用户（管理员功能）
     */
    public void unlockUser(String username) {

        clearLoginFailureRecord(username);
        log.info("用户 {} 已被解锁", username);
    }
}