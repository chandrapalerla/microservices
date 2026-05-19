package com.mysqlmongodb.mapper;

import com.mysqlmongodb.dto.UserDto;
import com.mysqlmongodb.mysql.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);

    User toEntity(UserDto dto);
}

