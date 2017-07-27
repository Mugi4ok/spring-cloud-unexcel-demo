package com.microservice.unexcel

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.stereotype.Component

@SpringBootApplication
@EnableDiscoveryClient
class UnexcelServiceApplication {

	static void main(String[] args) {
		SpringApplication.run UnexcelServiceApplication, args
	}
}

//@Component
//class DummyData implements CommandLineRunner {
//    @Autowired
//    IncomingFileRepository fileRepository
//
//    @Override
//    void run(String... args) throws Exception {
//        println "CLR is on!!!!!!!!!!!!!!1111111111111111111111111"
//        ['someXls.xls', 'another_file.xlsx'].each { fileRepository.save(new IncomingFile(it)) }
//        fileRepository.findAll()*.toString()
//
//    }
//}
