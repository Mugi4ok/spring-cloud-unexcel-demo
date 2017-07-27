package com.microservice.uiservice

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.hateoas.Resources
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate


@RestController
@RequestMapping('/unexcel')
class UnexcelController {

    @Autowired
    RestTemplate restTemplate

    @HystrixCommand(fallbackMethod = 'unexcelFallback')
    @RequestMapping(method = RequestMethod.POST)
    def callUnexcel(@RequestParam('filePath') String filePath) {
        HttpEntity<MultiValueMap> entity = new HttpEntity<>(
                new LinkedMultiValueMap([filePath: [filePath]]),
                new HttpHeaders() {{ this.setContentType(MediaType.APPLICATION_FORM_URLENCODED) }}
        )
        restTemplate.exchange('http://unexcel-service/unexcel', HttpMethod.POST, entity, List).body
    }

//    Configured to 60 seconds timeout; kinda beats the purpose, but unexcel could take some time
    def unexcelFallback(String filePath) {
        []
    }

    @RequestMapping(method = RequestMethod.GET, value = '/files')
    def test() {
        ParameterizedTypeReference<Resources<IncomingFile>> res = new ParameterizedTypeReference<Resources<IncomingFile>>() {}

        restTemplate.exchange('http://unexcel-service/incomingFiles', HttpMethod.GET, null, res).getBody().getContent()
    }

}
