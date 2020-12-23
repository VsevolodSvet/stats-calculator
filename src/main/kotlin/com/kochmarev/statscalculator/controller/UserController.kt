package com.kochmarev.statscalculator.controller

import com.kochmarev.statscalculator.entity.User
import com.kochmarev.statscalculator.service.SecurityService
import com.kochmarev.statscalculator.service.UserService
import com.kochmarev.statscalculator.validator.UserValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ModelAttribute

@Controller
class UserController {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var securityService: SecurityService

    @Autowired
    private lateinit var userValidator: UserValidator

    @GetMapping("/registration")
    fun registration(model: Model): String {
        model.addAttribute("userForm", User())
        return "registration"
    }

    @PostMapping("/registration")
    fun registration(@ModelAttribute("userForm") userForm: User, bindingResult: BindingResult, model: Model): String {
        userValidator.validate(userForm, bindingResult)
        if (bindingResult.hasErrors()) {
            return "registration"
        }
        userService.save(userForm)
        securityService.autoLogin(userForm.getUsername()!!, userForm.getConfirmPassword()!!)
        return "redirect:/welcome"
    }

    @GetMapping("/login")
    fun login(model: Model, error: String?, logout: String?): String {
        if (error != null) {
            model.addAttribute("error", "Username or password is incorrect.")
        }
        if (logout != null) {
            model.addAttribute("message", "Logged out successfully.")
        }
        return "login"
    }

    @GetMapping("/", "/welcome")
    fun welcome(model: Model): String {
        return "welcome"
    }

    @GetMapping("/admin")
    fun admin(model: Model): String {
        return "admin"
    }
}