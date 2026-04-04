package com.demo.rag_demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RagDemoApplication {

	@Value("${spring.ai.openai.api-key}")
	private String apiKey;

	public static void main(String[] args) {
		SpringApplication.run(RagDemoApplication.class, args);
	}

	/**
	 * 配置 ChatClient，使用 DeepSeek API
	 */
	@Bean
	public ChatClient chatClient() {

		OpenAiApi openAiApi = new OpenAiApi("https://api.deepseek.com", apiKey);

		OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
				.model("deepseek-chat")
				.temperature(0.7)
				.build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.defaultOptions(chatOptions)
				.build();

		return ChatClient.builder(chatModel).build();
	}
}