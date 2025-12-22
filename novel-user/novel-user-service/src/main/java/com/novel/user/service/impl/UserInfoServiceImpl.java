package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.novel.common.auth.JwtUtils;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.common.resp.RestResp;
import com.novel.config.exception.BusinessException;
import com.novel.user.dao.entity.UserFeedback;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserFeedbackMapper;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.dto.resp.UserLoginRespDto;
import com.novel.user.dto.resp.UserRegisterRespDto;
import com.novel.user.service.UserInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoMapper userInfoMapper;
    private final UserFeedbackMapper userFeedbackMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public UserInfoServiceImpl(UserInfoMapper userInfoMapper,
                               UserFeedbackMapper userFeedbackMapper,
                               @Qualifier("turtleRedisTemplate") RedisTemplate<String, Object> redisTemplate,
                               StringRedisTemplate stringRedisTemplate) {
        this.userInfoMapper = userInfoMapper;
        this.userFeedbackMapper = userFeedbackMapper;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public RestResp<UserRegisterRespDto> register(UserRegisterReqDto dto) {
        // 校验图形验证码是否正确
        if (!imgVerifyCodeOk(dto.getSessionId(), dto.getVelCode())) {
            // 图形验证码校验失败
            throw new BusinessException(ErrorCodeEnum.USER_VERIFY_CODE_ERROR);
        }

        // 校验手机号是否已注册
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserInfoTable.COLUMN_USERNAME, dto.getUsername())
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        if (userInfoMapper.selectCount(queryWrapper) > 0) {
            // 手机号已注册
            throw new BusinessException(ErrorCodeEnum.USER_NAME_EXIST);
        }

        // 注册成功，保存用户信息
        UserInfo userInfo = new UserInfo();
        userInfo.setPassword(
                DigestUtils.md5DigestAsHex(dto.getPassword().getBytes(StandardCharsets.UTF_8)));
        userInfo.setUsername(dto.getUsername());
        userInfo.setNickName(dto.getUsername());
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfo.setSalt("0");
        userInfoMapper.insert(userInfo);

        // 删除验证码
        removeImgVerifyCode(dto.getSessionId());

        // 生成JWT 并返回
        return RestResp.ok(
                UserRegisterRespDto.builder()
                        .token(JwtUtils.generateToken(userInfo.getId(), SystemConfigConsts.NOVEL_FRONT_KEY))
                        .uid(userInfo.getId())
                        .build()
        );
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

    @Override
    public RestResp<UserInfoRespDto> getUserInfo(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (Objects.isNull(userInfo)) {
            return null;
        }
        return RestResp.ok(UserInfoRespDto.builder()
                .id(userInfo.getId())
                .username(userInfo.getUsername())
                .nickName(userInfo.getNickName())
                .userSex(userInfo.getUserSex())
                .userPhoto(userInfo.getUserPhoto())
                .accountBalance(userInfo.getAccountBalance())
                .build()
        );
    }

    @Override
    public RestResp<List<UserInfoRespDto>> listUserInfoByIds(List<Long> userIds) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), userIds);
        return RestResp.ok(
                userInfoMapper.selectList(queryWrapper).stream().map(v -> UserInfoRespDto.builder()
                        .id(v.getId())
                        .username(v.getUsername())
                        .userPhoto(v.getUserPhoto())
                        .nickName(v.getNickName())
                        .build()).collect(Collectors.toList()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> updateUserInfo(UserInfoUptReqDto dto) {
        // 使用 LambdaUpdateWrapper 精确更新字段，避免实体 updateById 的潜在问题
        LambdaUpdateWrapper<UserInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserInfo::getId, dto.getUserId());

        if (dto.getNickName() != null) {
            updateWrapper.set(UserInfo::getNickName, dto.getNickName());
        }
        if (dto.getUserPhoto() != null) {
            updateWrapper.set(UserInfo::getUserPhoto, dto.getUserPhoto());
        }
        if (dto.getUserSex() != null) {
            updateWrapper.set(UserInfo::getUserSex, dto.getUserSex());
        }

        updateWrapper.set(UserInfo::getUpdateTime, LocalDateTime.now());

        userInfoMapper.update(null, updateWrapper);

        return RestResp.ok();
    }

    @Override
    public RestResp<Void> saveFeedback(Long userId, String content) {
        UserFeedback userFeedback = new UserFeedback();
        userFeedback.setUserId(userId);
        userFeedback.setContent(content);
        userFeedback.setCreateTime(LocalDateTime.now());
        userFeedback.setUpdateTime(LocalDateTime.now());
        userFeedbackMapper.insert(userFeedback);
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteFeedback(Long userId, Long id) {
        QueryWrapper<UserFeedback> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.CommonColumnEnum.ID.getName(), id)
                .eq(DatabaseConsts.UserFeedBackTable.COLUMN_USER_ID, userId);
        userFeedbackMapper.delete(queryWrapper);
        return RestResp.ok();
    }

    /**
     * 校验图形验证码
     */
    private boolean imgVerifyCodeOk(String sessionId, String verifyCode) {
        return Objects.equals(stringRedisTemplate.opsForValue()
                .get(CacheConsts.IMG_VERIFY_CODE_CACHE_KEY + sessionId), verifyCode);
    }

    /**
     * 从 Redis 中删除验证码
     */
    private void removeImgVerifyCode(String sessionId) {
        stringRedisTemplate.delete(CacheConsts.IMG_VERIFY_CODE_CACHE_KEY + sessionId);
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
        String lockKey = String.format(CacheConsts.USER_LOCK_STATUS, username);
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    /**
     * 获取锁定剩余时间，单位是分钟
     */
    private long getLockRemainTime(String username) {
        String lockKey = String.format(CacheConsts.USER_LOCK_STATUS, username);
        Long expire = redisTemplate.getExpire(lockKey, TimeUnit.MINUTES);
        return expire != null ? expire : 0;
    }

    /**
     * 处理登录失败
     */
    private void processLoginFailure(String username) {
        String failCountKey = String.format(CacheConsts.LOGIN_FAIL_COUNT, username);

        // 增加失败次数
        Long failCount = redisTemplate.opsForValue().increment(failCountKey);
        if (failCount != null && failCount == 1) {
            // 第一次失败，设置过期时间
            redisTemplate.expire(failCountKey, CacheConsts.LOCK_EXPIRE_TIME, TimeUnit.SECONDS);
        }

        // 检查是否达到锁定阈值
        if (failCount != null && failCount >= 5) {
            lockUser(username);
        }
    }

    /**
     * 锁定用户
     */
    private void lockUser(String username) {
        String lockKey = String.format(CacheConsts.USER_LOCK_STATUS, username);
        redisTemplate.opsForValue().set(lockKey, "LOCKED",
                CacheConsts.LOCK_EXPIRE_TIME, TimeUnit.SECONDS);

        // 清除失败计数
        String failCountKey = String.format(CacheConsts.LOGIN_FAIL_COUNT, username);
        redisTemplate.delete(failCountKey);

        log.warn("用户 {} 因连续登录失败被锁定", username);
    }

    /**
     * 获取剩余尝试次数
     */
    private int getRemainAttempts(String username) {
        String failCountKey = String.format(CacheConsts.LOGIN_FAIL_COUNT, username);
        Long failCount = (Long) redisTemplate.opsForValue().get(failCountKey);
        int currentCount = failCount != null ? failCount.intValue() : 0;
        return Math.max(0, 5 - currentCount - 1);
    }

    /**
     * 清除登录失败记录
     */
    private void clearLoginFailureRecord(String username) {
        String failCountKey = String.format(CacheConsts.LOGIN_FAIL_COUNT, username);
        String lockKey = String.format(CacheConsts.USER_LOCK_STATUS, username);

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



}

