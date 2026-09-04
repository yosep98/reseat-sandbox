# deploy/k3s

K3s 클러스터용 매니페스트. `deploy/docker-compose.deploy.yml`(EC2 단일 서버 배포)과 별개로, `large`(앱 노드)/`medium`(DB 노드, taint 격리) 2노드 K3s 클러스터로 옮기는 실습용.

- 설계 근거: `Dev/K3s/K3s-도입-아키텍처.md` (Obsidian)
- 실행 절차: `Dev/K3s/K3s-마이그레이션-가이드.md` (Obsidian)
- 작업 과정/트러블슈팅 기록: `Dev/K3s/K3s-작업로그.md` (Obsidian)

## 구조

```
deploy/k3s/
  00-namespace.yaml   # 전부 reseat 네임스페이스 하나로 (서비스별 분리는 네임스페이스가 아니라 아래 각 리소스 단위로)
  data/               # medium 노드 전용 — StatefulSet+PVC, nodeSelector: role=db + toleration
    mysql.yaml
    redis.yaml
    elasticsearch.yaml
  app/                # large 노드 — 기본 스케줄(별도 지정 없음)
    gateway-service.yaml
    user-service.yaml
    order-service.yaml
    performance-service.yaml
    frontend.yaml
  edge/
    ingress.yaml               # Traefik Ingress + RateLimit Middleware(IP 단위)
    cert-manager-issuer.yaml
```

적용 순서는 `Dev/K3s/K3s-마이그레이션-가이드.md`의 Step 순서를 따름: namespace → data(DB) → gateway-service → 나머지 앱 → edge(Ingress/TLS).
