package com.eighthours.tickgo.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eighthours.tickgo.user.entity.PassengerDO;
import com.eighthours.tickgo.user.entity.UserDO;
import com.eighthours.tickgo.user.mapper.PassengerMapper;
import com.eighthours.tickgo.user.mapper.UserMapper;
import com.eighthours.tickgo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PassengerMapper passengerMapper;

    @Override
    public Map<String, Object> getUserById(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        return result;
    }

    @Override
    public List<Map<String, Object>> getPassengersByUserId(Long userId) {
        List<PassengerDO> passengers = passengerMapper.selectList(
                new LambdaQueryWrapper<PassengerDO>()
                        .eq(PassengerDO::getUserId, userId));
        return passengers.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("realName", p.getRealName());
            map.put("idCard", p.getIdCard());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean validatePassengerBelongsToUser(Long userId, List<Long> passengerIds) {
        if (CollectionUtils.isEmpty(passengerIds)) {
            return true;
        }
        List<PassengerDO> passengers = passengerMapper.selectList(
                new LambdaQueryWrapper<PassengerDO>()
                        .eq(PassengerDO::getUserId, userId)
                        .in(PassengerDO::getId, passengerIds));
        return passengers.size() == passengerIds.size();
    }
}
