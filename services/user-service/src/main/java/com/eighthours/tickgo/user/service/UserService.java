package com.eighthours.tickgo.user.service;

import java.util.List;
import java.util.Map;

public interface UserService {

    Map<String, Object> getUserById(Long userId);

    List<Map<String, Object>> getPassengersByUserId(Long userId);

    boolean validatePassengerBelongsToUser(Long userId, List<Long> passengerIds);
}
