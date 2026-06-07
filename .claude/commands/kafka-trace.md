---
allowed-tools: Bash(docker:*), Read, Grep
argument-hint: [topic] (vd: payment.host.confirmed)
description: Debug Kafka — consume topic, consumer lag, kiểm tra DLT
---
Trace topic **$ARGUMENTS** trong cluster Kafka (container `kafka`):

1. Messages gần đây:
   `docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $ARGUMENTS --from-beginning --max-messages 10 --timeout-ms 5000`
2. Consumer group lag (LAG > 0 = message đang chờ xử lý / consumer kẹt):
   `docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups`
3. Dead Letter Topic (rule #7) — có message rớt vào `.DLT` không?
   `docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $ARGUMENTS.DLT --from-beginning --max-messages 5 --timeout-ms 5000`

Đối chiếu producer/consumer với **Topic Registry** trong .claude/rules/kafka-patterns.md. Nếu `.DLT` có message → báo cáo payload + nguyên nhân, gợi ý replay qua `POST /api/admin/kafka/replay?topic=$ARGUMENTS.DLT`.
