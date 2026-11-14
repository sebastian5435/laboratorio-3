package com.tdea.mcp_server_banco;

import java.util.List;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.tdea.mcp_server_banco.tools.BancoTools;
import com.tdea.mcp_server_banco.tools.CreditoTools;

@SpringBootApplication
public class McpServerBancoApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerBancoApplication.class, args);
    }

    /**
     * Registra en el servidor MCP todos los metodos anotados con @Tool.
     */
    @Bean
    public List<ToolCallback> tools(BancoTools bancoTools, CreditoTools creditoTools) {
        return List.of(ToolCallbacks.from(bancoTools, creditoTools));
    }
}
