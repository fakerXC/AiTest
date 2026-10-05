# ES 官方镜像不带 ik 插件，在线安装；ik 版本必须与 ES 版本严格一致
FROM docker.elastic.co/elasticsearch/elasticsearch:8.13.2
RUN bin/elasticsearch-plugin install -b https://get.infini.cloud/elasticsearch/analysis-ik/8.13.2
