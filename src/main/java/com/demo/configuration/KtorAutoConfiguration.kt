package com.demo.configuration

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.LocalVariableTableParameterNameDiscoverer
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestMethod.*
import java.lang.reflect.Method
import java.util.*
import javax.annotation.Resource
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.jvm.kotlinFunction


/**
 * @author theo
 * @date 2019/4/16
 */
@Configuration
@EnableConfigurationProperties(KtorProperties::class)
open class KtorAutoConfiguration() {

    @Resource
    private lateinit var properties: KtorProperties


    @Bean
    open fun applicationEngine(context: ApplicationContext): ApplicationEngine {
        return embeddedServer(Netty, properties.port, properties.host) {
            install(ContentNegotiation) {
                jackson { }
            }
            val beans = context.getBeansWithAnnotation(Controller::class.java).values

            val allDefinitions = beans.flatMap { bean ->
                val mappingAnnotation = bean.javaClass.getDeclaredAnnotation(RequestMapping::class.java)
                bean.javaClass.methods.filter {
                    it.isAnnotationPresent(RequestMapping::class.java)
                }.map {
                    val list = it.getDeclaredAnnotation(RequestMapping::class.java).value.flatMap { child ->
                        mappingAnnotation.value.map { parent ->
                            parent + if (child.startsWith("/")) child else "/$child"
                        }
                    }
                    RouteDefinition(it, bean, mappingAnnotation.method, list)
                }
            }
            routing {
                if (properties.enableTrace) {
                    trace {
                        println((it.buildText()))
                    }
                }
                install(StatusPages) {
                    exception<Throwable> { e ->
                        call.respondText(e.localizedMessage
                                ?: "未知错误", ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }
                route("/") {
                    allDefinitions.forEach {
                        mapping(it)
                    }
                }
            }
        }.start(wait = false)
    }

    private val discoverer = LocalVariableTableParameterNameDiscoverer()

    private fun Route.mapping(definition: RouteDefinition) {
        val method = definition.method
        val bean = definition.bean
        if (definition.methods.isEmpty()) {
            definition.methods = values()
        }

        definition.methods.forEach { requestMethod ->
            definition.uri.forEach { uri ->
                when (requestMethod) {
                    GET -> {
                        get(uri) {
                            //FIXME too many reflections
                            val params = call.parameters
                            val param = discoverer.getParameterNames(method)?.filter(Objects::nonNull)?.map {
                                params[it]
                            }?.toTypedArray()!!
                            val message = method.invokeSuspend(bean, param)
                            message?.let { call.respond(message) }
                        }
                    }
                    POST -> {
                        post(uri) {
                        }
                    }
//                HEAD -> TODO()
//                PUT -> TODO()
//                PATCH -> TODO()
//                DELETE -> TODO()
//                OPTIONS -> TODO()
//                TRACE -> TODO()
                    else -> {
                    }
                }
            }
        }
    }
}

@ConfigurationProperties(prefix = "spring.ktor")
open class KtorProperties {
    open var host: String = "0.0.0.0"
    open var port: Int = 8088
    open var enableTrace = false
}


data class RouteDefinition(val method: Method, val bean: Any, var methods: Array<RequestMethod>, val uri: List<String>)

/**
 * invoke method
 * if its suspend, set continuation
 */
suspend fun Method.invokeSuspend(obj: Any, args: Array<*>): Any? =
        if (this.kotlinFunction != null && this.kotlinFunction!!.isSuspend) {
            suspendCoroutineUninterceptedOrReturn<Any> {
                //add continuation at the end of args
                val list = arrayListOf(*args)
                list[list.size - 1] = it
                invoke(obj, *list.toArray())
            }
        } else {
            invoke(obj, *args)
        }