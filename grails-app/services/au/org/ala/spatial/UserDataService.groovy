package au.org.ala.spatial
/**
 * A small cache for data resource attribution.
 * This should be revisited if this cache grows or needs regular refreshing.
 */
class UserDataService {

    UDHeader put(String user_id, String record_type, String description, String metadata, String data_path, String analysis_id) {
        Date upload_dt = new Date(System.currentTimeMillis())
        UDHeader udHeader = new UDHeader(
                user_id: user_id,
                record_type: record_type,
                description: description,
                metadata: metadata,
                data_path: data_path,
                analysis_id: analysis_id,
                upload_dt: upload_dt
        )

        UDHeader.withTransaction {
            if (!udHeader.save(flush: true)) {
                udHeader.errors.allErrors.each { error ->
                    log.error("Error saving UDHeader: ${error}")
                }
                return null
            }
        }

        return udHeader
    }

    UDHeader update(Long ud_header_id, String user_id, String record_type, String description, String metadata, String data_path, String analysis_id) {
        UDHeader.withTransaction {
            UDHeader udHeader = UDHeader.get(ud_header_id)
            if (udHeader != null) {
                udHeader.user_id = user_id
                udHeader.record_type = record_type
                udHeader.description = description
                udHeader.metadata = metadata
                udHeader.data_path = data_path
                udHeader.analysis_id = analysis_id
                udHeader.upload_dt = new Date(System.currentTimeMillis())

                if (!udHeader.save(flush: true)) {
                    udHeader.errors.allErrors.each { error ->
                        log.error("Error saving UDHeader: ${error}")
                    }
                    return null
                }
            }

            return udHeader
        }
    }

    boolean delete(Long ud_header_id) {
        UDHeader.withTransaction {
            UDHeader.get(ud_header_id).delete()
        }

        return true
    }

    List<UDHeader> searchDescAndTypeOr(String desc, String record_type, String user_id, String data_path, String analysis_id, int start, int limit) {
        def criteria = UDHeader.createCriteria()
        def results = criteria.list(max: limit, offset: start) {
            if (desc) {
                ilike('description', desc)
            }
            if (record_type) {
                eq('record_type', record_type)
            }
            or {
                if (user_id) {
                    eq('user_id', user_id)
                }
                if (data_path) {
                    eq('data_path', data_path)
                }
                if (analysis_id) {
                    eq('analysis_id', analysis_id)
                }
            }
        }
        return results
    }
}
