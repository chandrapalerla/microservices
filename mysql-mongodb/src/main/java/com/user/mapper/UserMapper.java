package com.user.mapper;

import com.user.dto.UserDto;
import com.user.mysql.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);

    User toEntity(UserDto dto);
}

