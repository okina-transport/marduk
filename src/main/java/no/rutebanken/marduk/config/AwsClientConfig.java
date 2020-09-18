/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("aws-blobstore")
public class AwsClientConfig {

    @Value("${blobstore.aws.access.key}")
    private String key;

    @Value("${blobstore.aws.access.secret}")
    private String secret;

    @Bean
    public AmazonS3 client() {
        return AmazonS3Client.builder()
                .withRegion("eu-west-1")
                .withClientConfiguration(new ClientConfiguration()
                        .withMaxConnections(500))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(key, secret)))
                .build();
    }
}
