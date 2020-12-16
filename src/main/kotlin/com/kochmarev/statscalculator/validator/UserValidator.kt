package com.kochmarev.statscalculator.validator

import com.kochmarev.statscalculator.entity.User
import com.kochmarev.statscalculator.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
import org.springframework.validation.ValidationUtils
import org.springframework.validation.Validator

@Component
class UserValidator : Validator {

    @Autowired
    private var userService: UserService? = null

    override fun supports(aClass: Class<*>): Boolean {
        return User::class.java == aClass
    }

    override fun validate(o: Any, errors: Errors) {
        val user: User = o as User
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "username", "Required")
        if (user.getUsername().isNullOrBlank() || user.getUsername()!!.length > 32) {
            errors.rejectValue("username", "Size.userForm.username")
        }
        if (userService!!.findByUsername(user.getUsername()) != null) {
            errors.rejectValue("username", "Duplicate.userForm.username")
        }
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "Required")
        if (user.getPassword()!!.length < 6 || user.getPassword()!!.length > 32) {
            errors.rejectValue("password", "Size.userForm.password")
        }
        if (!user.getConfirmPassword().equals(user.getPassword())) {
            errors.rejectValue("confirmPassword", "Different.userForm.password")
        }
    }
}