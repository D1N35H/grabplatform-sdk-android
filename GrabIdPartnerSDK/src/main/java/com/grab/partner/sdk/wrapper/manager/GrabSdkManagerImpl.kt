package com.grab.partner.sdk.wrapper.manager

import android.content.Context
import com.grab.partner.sdk.*

import com.grab.partner.sdk.models.GrabIdPartnerError
import com.grab.partner.sdk.utils.IUtility
import com.grab.partner.sdk.wrapper.di.DaggerWrapperComponent
import com.grab.partner.sdk.wrapper.di.WrapperComponent

import java.util.concurrent.ConcurrentHashMap

import javax.inject.Inject

import com.grab.partner.sdk.GrabIdPartner.Companion.RESPONSE_STATE
import com.grab.partner.sdk.GrabIdPartner.Companion.RESPONSE_TYPE
import com.grab.partner.sdk.models.GrabIdPartnerErrorCode
import com.grab.partner.sdk.models.GrabIdPartnerErrorDomain

const val EMPTY_STRING_CONST = ""
class GrabSdkManagerImpl private constructor(): GrabSdkManager {

    private object Holder {
        fun createInstance() = GrabSdkManagerImpl()
    }

    companion object {
        lateinit var component: WrapperComponent

        @Volatile private var INSTANCE: GrabSdkManagerImpl? = null

        fun getInstance(): GrabSdkManagerImpl =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: Holder.createInstance().also { INSTANCE = it }
                }
    }

    @Inject lateinit var utility: IUtility
    @Inject lateinit var grabIdPartner: GrabIdPartnerProtocol
    @Inject lateinit var loginApi: GrabLoginApi
    @Inject lateinit var sessions: ConcurrentHashMap<String, GrabSdkManager.Builder>
    @Inject lateinit var clientStates: ConcurrentHashMap<String, String>
    private var context: Context? = null

    override fun init(context: Context) {
        component = DaggerWrapperComponent.builder().build()
        component.inject(this)
        this.context = context
        grabIdPartner.initialize(context)
    }

    override fun doLogin(context: Context, clientId: String) {
        val state = clientStates[clientId]
        val builder = sessions[state]

        if (state != null && builder != null) {
            loginApi.doLogin(context, state, builder)
        }
    }


    internal fun returnResult(result: String) {
        val code = utility.getURLParam(RESPONSE_TYPE, result)
        val state = utility.getURLParam(RESPONSE_STATE, result)
        val builder = sessions[state]

        builder?.let {
            if (code == null || code.isEmpty()) {
                builder.listener?.onError(GrabIdPartnerError(GrabIdPartnerErrorDomain.EXCHANGETOKEN, GrabIdPartnerErrorCode.invalidCode,
                        utility.readResourceString(context, R.string.ERROR_MISSING_CODE), null))
                return
            }

            val loginSession = builder.loginSession
            loginSession.codeInternal = code

            if(builder.exchangeRequired) {
                loginSession.let {
                    loginApi.exchangeToken(it, result, builder)
                }
            }
            else {
                if (builder.listener != null)
                    builder.listener?.onSuccess(loginSession)
            }
        }

    }

    override fun teardown() {
        context = null
        grabIdPartner.teardown()
        sessions.clear()
        clientStates.clear()
        INSTANCE = null
    }
}
