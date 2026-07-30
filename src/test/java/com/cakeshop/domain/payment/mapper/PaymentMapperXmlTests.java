package com.cakeshop.domain.payment.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// PaymentMapper 인터페이스와 XML의 연결 누락을 잡는 안전장치다.
class PaymentMapperXmlTests {

    @Test
    void mapperInterfaceAndXmlStatementsStaySynchronized() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/payment/PaymentMapper.xml";

        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder parser = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            );
            parser.parse();
        }

        String namespace = PaymentMapper.class.getName();
        Set<String> interfaceMethods = Arrays.stream(PaymentMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> xmlStatements = configuration.getMappedStatementNames().stream()
                .filter(name -> name.startsWith(namespace + "."))
                .map(name -> name.substring(namespace.length() + 1))
                .collect(Collectors.toSet());

        assertThat(xmlStatements).containsExactlyInAnyOrderElementsOf(interfaceMethods);
    }
}
