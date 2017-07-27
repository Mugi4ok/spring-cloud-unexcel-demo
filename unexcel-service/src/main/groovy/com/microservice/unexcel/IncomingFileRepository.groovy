package com.microservice.unexcel

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.data.rest.core.annotation.RestResource

@RepositoryRestResource
interface IncomingFileRepository extends JpaRepository<IncomingFile, Long> {
    @RestResource(path = 'by-name')
    IncomingFile findByFileName(@Param('name') String name)
}
