package com.lawfirm.law.firm.config;

import com.lawfirm.law.firm.dto.ClientMapper;
import com.lawfirm.law.firm.repository.ClientRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;

@Profile("postgres")
@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class DataInitializer implements ApplicationRunner {

    private final ClientRepository repository;
    private final ClientMapper mapper;

    public DataInitializer(ClientRepository repository, ClientMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
 
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // intentionally empty - data should come from the database only
    }
}
