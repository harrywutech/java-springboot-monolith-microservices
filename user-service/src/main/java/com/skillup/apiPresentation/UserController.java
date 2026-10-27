package com.skillup.apiPresentation;

import com.skillup.apiPresentation.dto.in.UserInDto;
import com.skillup.apiPresentation.dto.in.UserPin;
import com.skillup.apiPresentation.dto.out.UserOutDto;
import com.skillup.apiPresentation.util.SkillUpCommon;
import com.skillup.apiPresentation.util.SkillUpResponse;
import com.skillup.domain.user.UserDomain;
import com.skillup.domain.user.UserService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
public class UserController {
  @Autowired UserService userService;

  @PostMapping
  public ResponseEntity<SkillUpResponse> createUser(@RequestBody UserInDto userInDto) {
    UserDomain userDomain;
    // insert data into user table'
    try {
      userDomain = userService.createUser(toDomain(userInDto));
      return ResponseEntity.status(SkillUpCommon.SUCCESS)
          .body(SkillUpResponse.builder().result(toOutDto(userDomain)).msg(null).build());
    } catch (org.springframework.dao.DuplicateKeyException e) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST)
          .body(
              SkillUpResponse.builder()
                  .result(null)
                  .msg(String.format(SkillUpCommon.USER_EXISTS, userInDto.getUserName()))
                  .build());
    }
  }

  @GetMapping("/id/{id}")
  public ResponseEntity<SkillUpResponse> readAccountById(@PathVariable("id") String accountId) {
    UserDomain userDomain = userService.readAccountById(accountId);
    // handle userDomain is null
    if (Objects.isNull(userDomain)) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST)
          .body(
              SkillUpResponse.builder()
                  .msg(String.format(SkillUpCommon.USER_ID_WRONG, accountId))
                  .build());
    }
    return ResponseEntity.status(SkillUpCommon.SUCCESS)
        .body(SkillUpResponse.builder().result(toOutDto(userDomain)).build());
  }

  @GetMapping("/name/{name}")
  public ResponseEntity<SkillUpResponse> readAccountByName(@PathVariable("name") String userName) {
    UserDomain userDomain = userService.readAccountByName(userName);
    // handle userDomain is null
    if (Objects.isNull(userDomain)) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST)
          .body(
              SkillUpResponse.builder()
                  .msg(String.format(SkillUpCommon.USER_NAME_WRONG, userName))
                  .build());
    }
    return ResponseEntity.status(SkillUpCommon.SUCCESS)
        .body(SkillUpResponse.builder().result(toOutDto(userDomain)).build());
  }

  @PostMapping("/login")
  public ResponseEntity<SkillUpResponse> login(@RequestBody UserInDto userInDto) {
    // 1 get user by name 1.1 userName is wrong 400
    UserDomain userDomain = userService.readAccountByName(userInDto.getUserName());
    if (Objects.isNull(userDomain)) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST)
          .body(
              SkillUpResponse.builder()
                  .msg(String.format(SkillUpCommon.USER_NAME_WRONG, userInDto.getUserName()))
                  .build());
    }
    // 2 check password match, password wrong, return 400
    if (!Objects.equals(userInDto.getPassword(), userDomain.getPassword())) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST)
          .body(SkillUpResponse.builder().msg(SkillUpCommon.PASSWORD_NOT_MATCH).build());
    }
    // 3 if matched, return 200 plus the course shard marker
    return ResponseEntity.status(SkillUpCommon.SUCCESS)
        .header("mark", String.valueOf(com.skillup.common.Shard.of(userDomain.getUserId())))
        .body(SkillUpResponse.builder().result(toOutDto(userDomain)).build());
  }

  @PutMapping("/password")
  public ResponseEntity<SkillUpResponse> updatePassword(@RequestBody UserPin userPin) {
    // 1 get user, 1.1 userName is wrong 400
    UserDomain userDomain = userService.readAccountByName(userPin.getUserName());
    if (Objects.isNull(userDomain)) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST)
          .body(
              SkillUpResponse.builder()
                  .msg(String.format(SkillUpCommon.USER_NAME_WRONG, userPin.getUserName()))
                  .build());
    }
    // 2 check password match, password wrong, return 400
    if (!Objects.equals(userPin.getOldPassword(), userDomain.getPassword())) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST)
          .body(SkillUpResponse.builder().msg(SkillUpCommon.PASSWORD_NOT_MATCH).build());
    }
    // 3 if matched, update password and save, return
    userDomain.setPassword(userPin.getNewPassword());
    userService.updateUser(userDomain);
    return ResponseEntity.status(SkillUpCommon.SUCCESS)
        .body(SkillUpResponse.builder().result(toOutDto(userDomain)).build());
  }

  private UserDomain toDomain(UserInDto userInDto) {
    return UserDomain.builder()
        .userId(UUID.randomUUID().toString())
        .userName(userInDto.getUserName())
        .password(userInDto.getPassword())
        .build();
  }

  private UserOutDto toOutDto(UserDomain userDomain) {
    return UserOutDto.builder()
        .userId(userDomain.getUserId())
        .userName(userDomain.getUserName())
        .build();
  }
}
