package com.github.diovanibmotta.microkernel.kernel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class KernelApiTest {

    static Path pluginsDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws IOException {
        pluginsDir = Files.createTempDirectory("kernel-plugins");
        TestPlugins.buildJar(pluginsDir, "cheap.jar", "cheap", "1.0", TestPlugins.ONE_QUOTE_BODY);
        registry.add("kernel.plugins-dir", pluginsDir::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired PluginManager manager;

    @Test
    void quotesThroughLoadedPluginAndListsIt() throws Exception {
        manager.scan();
        mvc.perform(get("/plugins")).andExpect(status().isOk())
                .andExpect(jsonPath("$.active[0].id").value("cheap"));
        mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originZip\":\"01000\",\"destinationZip\":\"20000\",\"weightKg\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes[0].pluginId").value("cheap"));
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mvc.perform(post("/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originZip\":\"abc\",\"destinationZip\":\"20000\",\"weightKg\":0}"))
                .andExpect(status().isBadRequest());
    }
}
