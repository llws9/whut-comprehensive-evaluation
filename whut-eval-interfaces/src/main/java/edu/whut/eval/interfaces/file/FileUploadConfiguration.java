package edu.whut.eval.interfaces.file;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileUploadProperties.class)
public class FileUploadConfiguration {
}
