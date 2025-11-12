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
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.dto.resp.UserLoginRespDto;
import com.novel.user.dto.resp.UserRegisterRespDto;
import com.novel.user.manager.redis.VerifyCodeManager;
import com.novel.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserInfoMapper userInfoMapper;

    private final VerifyCodeManager verifyCodeManager;


    @Override
    public RestResp<UserRegisterRespDto> register(UserRegisterReqDto dto) {
        // 校验图形验证码是否正确
        if (!verifyCodeManager.imgVerifyCodeOk(dto.getSessionId(), dto.getVelCode())) {
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
        verifyCodeManager.removeImgVerifyCode(dto.getSessionId());

        // 生成JWT 并返回
        return RestResp.ok(
                UserRegisterRespDto.builder()
                        .token(JwtUtils.generateToken(userInfo.getId(), SystemConfigConsts.NOVEL_FRONT_KEY))
                        .uid(userInfo.getId())
                        .build()
        );
    }


    @Override
    public RestResp<UserLoginRespDto> login(UserLoginReqDto userLoginReqDto) {
//        查询用户信息
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.UserInfoTable.COLUMN_USERNAME, userLoginReqDto.getUsername())
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        UserInfo userInfo = userInfoMapper.selectOne(queryWrapper);
//        用户不存在
        if (Objects.isNull(userInfo)) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }
//        判断密码是否正确
        if(!Objects.equals(userInfo.getPassword(),
                DigestUtils.md5DigestAsHex(userLoginReqDto.getPassword().getBytes(StandardCharsets.UTF_8)))) {
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);
        }

//        登录成功
        return RestResp.ok(UserLoginRespDto.builder()
                .token(JwtUtils.generateToken(userInfo.getId(), SystemConfigConsts.NOVEL_FRONT_KEY))
                .userId(userInfo.getId())
                .nickName(userInfo.getNickName())
                .build()
        );

    }

    public RestResp<UserInfoRespDto> getUserInfo(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        return RestResp.ok(UserInfoRespDto.builder()
                .nickName(userInfo.getUsername())
                .userSex(userInfo.getUserSex())
                .userPhoto(userInfo.getUserPhoto())
                .build()
        );
    }
}
