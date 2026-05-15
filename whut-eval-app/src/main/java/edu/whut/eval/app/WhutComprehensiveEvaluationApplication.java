package edu.whut.eval.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "edu.whut.eval")
@MapperScan("edu.whut.eval.infra.persistence.mapper")
public class WhutComprehensiveEvaluationApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhutComprehensiveEvaluationApplication.class, args);
    }
}
