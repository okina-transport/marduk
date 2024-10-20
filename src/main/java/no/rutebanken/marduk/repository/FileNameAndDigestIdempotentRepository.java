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

package no.rutebanken.marduk.repository;


import no.rutebanken.marduk.domain.FileNameAndDigest;
import org.apache.camel.processor.idempotent.jdbc.AbstractJdbcMessageIdRepository;

import javax.sql.DataSource;
import java.sql.Timestamp;

/**
 * Custom impl of JDBC messageId repo considering both file name and digest as unique keys.
 *
 * MessageId format is JSON because camel forces conversion to String.
 */
public class FileNameAndDigestIdempotentRepository extends AbstractJdbcMessageIdRepository<String> {

	private String queryString = "SELECT COUNT(*) FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND (digest = ? or fileName=?)";
	private String insertString = "INSERT INTO CAMEL_UNIQUE_FILENAME_AND_DIGEST (processorName, digest,fileName, createdAt) VALUES (?,?, ?, ?)";
	private String deleteString = "DELETE FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND digest = ? and fileName=?";
	private String clearString = "DELETE FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ?";


	public FileNameAndDigestIdempotentRepository(DataSource dataSource, String processorName) {
		super(dataSource, processorName);
	}


	protected int queryForInt(String keyAsString) {
		FileNameAndDigest key=FileNameAndDigest.fromString(keyAsString);
		return ((Integer) this.jdbcTemplate.queryForObject(this.queryString, Integer.class, new Object[]{this.processorName, key.getDigest(), key.getFileName()})).intValue();
	}

	protected int insert(String keyAsString) {
		FileNameAndDigest key=FileNameAndDigest.fromString(keyAsString);
		return this.jdbcTemplate.update(this.insertString, new Object[]{this.processorName, key.getDigest(), key.getFileName(), new Timestamp(System.currentTimeMillis())});
	}

	protected int delete(String keyAsString) {
		FileNameAndDigest key=FileNameAndDigest.fromString(keyAsString);
		return this.jdbcTemplate.update(this.deleteString, new Object[]{this.processorName, key.getDigest(), key.getFileName()});
	}

	protected int delete() {
		return this.jdbcTemplate.update(this.clearString, new Object[]{this.processorName});
	}

}
