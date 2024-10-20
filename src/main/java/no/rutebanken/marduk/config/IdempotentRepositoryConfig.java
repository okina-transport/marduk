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

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import no.rutebanken.marduk.repository.UniqueDigestPerFileNameIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

public class IdempotentRepositoryConfig {

	@Autowired
	private DataSource datasource;

	@Bean
	public IdempotentRepository fileNameAndDigestIdempotentRepository() {
		return new FileNameAndDigestIdempotentRepository(datasource, "nameAndDigest");
	}

	@Bean
	public IdempotentRepository idempotentDownloadRepository() {
		return new UniqueDigestPerFileNameIdempotentRepository(datasource, "uniqueDigestForName");
	}

}
