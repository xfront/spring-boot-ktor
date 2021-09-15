package com.sample

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping(value = ["/", "/foo"])
open class FooController(private val fooService: FooService) {

    @RequestMapping(value = ["bar", "bax"], method = [RequestMethod.POST, RequestMethod.GET])
    suspend fun local(
        @RequestBody user: User?,
        @CookieValue("cook") cookie: String?,
        @RequestHeader("token") token: String?,
        bar: String?
    ): String {
        delay(1000)
        GlobalScope.launch {
            delay(2000)
        }
        return fooService.bar()
    }
}

@Service
class FooService {

    suspend fun bar(): String {
        delay(1000)
        return "voila"
    }
}

data class User(private val id: Int, private val name: String)