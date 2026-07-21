package com.example.ragdemo.store;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/account")
public class StoreAccountController {

    private final StoreUserService userService;

    public StoreAccountController(StoreUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public StoreApiModels.UserResponse account() {
        return userService.current();
    }

    @PutMapping
    public StoreApiModels.UserResponse update(@RequestBody StoreApiModels.UpdateProfileRequest request) {
        return userService.update(request);
    }
}
