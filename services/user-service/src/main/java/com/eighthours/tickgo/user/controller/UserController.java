package com.eighthours.tickgo.user.controller;

import com.eighthours.tickgo.user.common.Result;
import com.eighthours.tickgo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/test")
    public Result<String> test() {
        return Result.success("OK");
    }

    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getUserById(@PathVariable Long userId) {
        Map<String, Object> user = userService.getUserById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    @GetMapping("/{userId}/passengers")
    public Result<List<Map<String, Object>>> getPassengersByUserId(@PathVariable Long userId) {
        List<Map<String, Object>> passengers = userService.getPassengersByUserId(userId);
        return Result.success(passengers);
    }

    @PostMapping("/validate-passengers")
    public Result<Boolean> validatePassengerBelongsToUser(@RequestBody Map<String, Object> request) {
        Long userId = ((Number) request.get("userId")).longValue();
        @SuppressWarnings("unchecked")
        List<Long> passengerIds = (List<Long>) request.get("passengerIds");
        boolean valid = userService.validatePassengerBelongsToUser(userId, passengerIds);
        if (!valid) {
            return Result.error("存在不属于当前用户的乘车人");
        }
        return Result.success(true);
    }
}
