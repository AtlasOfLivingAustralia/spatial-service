FROM kartoza/geoserver:2.23.2

RUN apt-get -y update && apt-get install -y wget netcat

RUN mkdir -p /data/geoserver_data_dir/gwc \
  /data/geoserver_data_dir/footprints_dir \
  /data/geoserver_data_dir/GeoNetwork_opensource \
  /data/geoserver_data_dir/logs

RUN touch /data/geoserver_data_dir/logs/geoserver.log

RUN mkdir -p /data/geoserver-files
COPY geoserver-files/* /data/geoserver-files/
RUN chmod a+x /data/geoserver-files/geoserver.sh

VOLUME /data/geoserver_data_dir
