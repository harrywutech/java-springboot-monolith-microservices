package com.skillup.domain.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    UserRepository userRepository;

    public UserDomain createUser(UserDomain userDomain){
        // insert into database
        userRepository.createUser(userDomain);
        return userDomain;
    }

    public UserDomain readAccountById(String accountId) {
        // return userDomain or null
        return userRepository.readUserById(accountId);
    }

    public UserDomain readAccountByName(String userName) {
        return userRepository.readUserByName(userName);
    }

    public UserDomain updateUser(UserDomain userDomain) {
        userRepository.updateUser(userDomain);
        return userDomain;
    }
}
