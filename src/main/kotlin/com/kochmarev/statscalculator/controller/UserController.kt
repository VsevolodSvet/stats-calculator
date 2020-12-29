package com.kochmarev.statscalculator.controller

import com.kochmarev.statscalculator.entity.User
import com.kochmarev.statscalculator.service.SecurityService
import com.kochmarev.statscalculator.service.UserService
import com.kochmarev.statscalculator.validator.UserValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@Controller
class UserController {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var securityService: SecurityService

    @Autowired
    private lateinit var userValidator: UserValidator

    @RequestMapping(value = ["/registration"], method = [RequestMethod.GET])
    fun registration(model: Model): String {
        model.addAttribute("userForm", User())
        return "registration"
    }

    @RequestMapping(value = ["/registration"], method = [RequestMethod.POST])
    fun registration(@ModelAttribute("userForm") userForm: User, bindingResult: BindingResult, model: Model?): String {
        userValidator.validate(userForm, bindingResult)
        if (bindingResult.hasErrors()) {
            return "registration"
        }
        userService.save(userForm)
        securityService.autoLogin(userForm.getUsername()!!, userForm.getConfirmPassword()!!)
        return "redirect:/welcome"
    }

    @RequestMapping(value = ["/login"], method = [RequestMethod.GET])
    fun login(model: Model, error: String?, logout: String?): String {
        if (error != null) {
            model.addAttribute("error", "Username or password is incorrect.")
        }
        if (logout != null) {
            model.addAttribute("message", "Logged out successfully.")
        }
        return "login"
    }

    @RequestMapping(value = ["/", "/welcome"], method = [RequestMethod.GET])
    fun welcome(model: Model?): String {
        return "welcome"
    }

    @RequestMapping(value = ["/admin"], method = [RequestMethod.GET])
    fun admin(model: Model?): String {
        return "admin"
    }
}