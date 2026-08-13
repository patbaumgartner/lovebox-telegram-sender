package com.patbaumgartner.lovebox.telegram.sender.config;

import com.patbaumgartner.lovebox.telegram.sender.lovebox.CheckEmailRequestBody;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.CheckEmailResponseBody;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.GraphqlRequestBody;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.LoginWithPasswordRequestBody;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.LoginWithPasswordResponseBody;
import com.patbaumgartner.lovebox.telegram.sender.telegram.TelegramBotsRuntimeHints;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Central registration of GraalVM native-image hints for the application.
 * <p>
 * Imports {@link TelegramBotsRuntimeHints} (reflection metadata for the Telegram Bot API
 * types, which the telegrambots library does not provide) and {@link AwtRuntimeHints}
 * (JNI metadata for the AWT/ImageIO rendering pipeline). Without these hints the native
 * image cannot (de)serialise Bot API payloads with Jackson or decode photos.
 * <p>
 * {@code @RegisterReflectionForBinding} covers the Lovebox API request/response records,
 * which are (de)serialised by Jackson through the {@code LoveboxRestClient} HTTP
 * interface.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints({ TelegramBotsRuntimeHints.class, AwtRuntimeHints.class })
@RegisterReflectionForBinding({ CheckEmailRequestBody.class, CheckEmailResponseBody.class, GraphqlRequestBody.class,
		LoginWithPasswordRequestBody.class, LoginWithPasswordResponseBody.class })
public class NativeHintsConfiguration {

}
