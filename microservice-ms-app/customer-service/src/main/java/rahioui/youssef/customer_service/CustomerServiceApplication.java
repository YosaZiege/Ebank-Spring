package rahioui.youssef.customer_service;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import rahioui.youssef.customer_service.entities.Customer;
import rahioui.youssef.customer_service.service.CustomerService;

@SpringBootApplication
public class CustomerServiceApplication {

    private final CustomerService customerService;

    CustomerServiceApplication(CustomerService customerService) {
        this.customerService = customerService;
    }

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);

    }

    @Bean
    CommandLineRunner commandLineRunner(CustomerService customerService) {
        return args -> {
            List<String> names = List.of("mohammed", "sohaib", "yasmine");
            names.forEach(name -> {
                customerService.saveCustomer(Customer.builder()
                        .name(name).email(name + "@gmail.com")
                        .build());
            });
        };
    }

}
