# Деплой на AttendTrack — AWS EC2 (Amazon Linux) + Docker + HTTPS

**Стек:** Spring Boot (Java 21) · Next.js 16 · PostgreSQL 16 · Nginx · Docker Compose  
**OS:** Amazon Linux 2023  
**HTTPS:** Let's Encrypt чрез [sslip.io](https://sslip.io) — безплатен DNS за всеки IP  
**URL:** https://52.58.114.218.sslip.io

---

## Стъпка 1 — Създай EC2 инстанция

1. Влез в **AWS Console → EC2 → Launch Instance**
2. **Name:** `attendtrack-prod`
3. **AMI:** Amazon Linux 2023 AMI
4. **Instance type:** `t3.small` минимум (2 vCPU, 2 GB RAM)  
   > `t3.micro` може да не е достатъчен — Java + Next.js build изискват памет
5. **Key pair:** Create new key pair → RSA → `.pem` → запази сигурно
6. **Security Group:**

| Тип   | Порт | Source    |
|-------|------|-----------|
| SSH   | 22   | My IP     |
| HTTP  | 80   | 0.0.0.0/0 |
| HTTPS | 443  | 0.0.0.0/0 |

7. **Storage:** минимум **20 GB** gp3
8. **Launch Instance**

---

## Стъпка 2 — Задай Elastic IP (задължително за Let's Encrypt)

Let's Encrypt изисква стабилен IP — по подразбиране AWS дава динамичен.

1. AWS Console → **EC2 → Elastic IPs → Allocate Elastic IP address**
2. **Associate Elastic IP** → избери инстанцията
3. Elastic IP е безплатен докато инстанцията върви

---

## Стъпка 3 — Свържи се по SSH

```bash
chmod 400 /path/to/your-key.pem
ssh -i /path/to/your-key.pem ec2-user@52.58.114.218
```

След като си вътре, стани root:
```bash
sudo su -
```

---

## Стъпка 4 — Обнови системата

```bash
dnf update -y
```

---

## Стъпка 5 — Инсталирай Docker и Docker Compose

```bash
# Инсталирай Docker
dnf install -y docker

# Стартирай и активирай при boot
systemctl enable --now docker

# Инсталирай Docker Compose V2 (не е включен в пакета)
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Провери
docker --version
docker compose version
```

---

## Стъпка 6 — Инсталирай Certbot

```bash
dnf install -y certbot
```

---

## Стъпка 7 — Вземи SSL сертификат

```bash
certbot certonly --standalone -d 52.58.114.218.sslip.io
```

Certbot ще поиска имейл адрес за известия при изтичане.  
Сертификатът е валиден **90 дни** — виж Стъпка 13 за автоматично подновяване.

След успех сертификатите са в:
```
/etc/letsencrypt/live/52.58.114.218.sslip.io/fullchain.pem
/etc/letsencrypt/live/52.58.114.218.sslip.io/privkey.pem
```

---

## Стъпка 8 — Билдни и публикувай образите в Docker Hub

Изпълни **от твоята локална машина**.

### 8а. Логни се в Docker Hub

```bash
docker login
```

### 8б. Билдни образите

```bash
cd /path/to/AttendTrack

docker build -t <DOCKERHUB_USERNAME>/attendtrack-backend:latest .
docker build -t <DOCKERHUB_USERNAME>/attendtrack-frontend:latest ./frontend
```

### 8в. Публикувай в Docker Hub

```bash
docker push <DOCKERHUB_USERNAME>/attendtrack-backend:latest
docker push <DOCKERHUB_USERNAME>/attendtrack-frontend:latest
```

---

## Стъпка 9 — Качи конфигурацията на сървъра

Само конфигурационните файлове се качват — кодът идва от Docker Hub.

**От твоята локална машина:**

```bash
cd /path/to/AttendTrack

scp -i /path/to/your-key.pem docker-compose.prod.yml nginx.conf .env.prod.example \
    ec2-user@52.58.114.218:/tmp/
```

**На EC2:**

```bash
mkdir /opt/attendtrack
mv /tmp/docker-compose.prod.yml /tmp/nginx.conf /tmp/.env.prod.example /opt/attendtrack/
cd /opt/attendtrack
```

---

## Стъпка 10 — Генерирай VAPID ключове (Web Push)

```bash
# Инсталирай Node.js
dnf install -y nodejs npm

# Генерирай ключовете
npx web-push generate-vapid-keys
```

Запиши изхода — нужен е за `.env.prod`:
```
Public Key:  BExampl3...
Private Key: xAmpl3...
```

---

## Стъпка 11 — Създай .env.prod

```bash
cd /opt/attendtrack
cp .env.prod.example .env.prod
nano .env.prod
```

Попълни всички стойности:

```env
# Docker Hub
DOCKER_USERNAME=       # твоето Docker Hub потребителско име

# Database
DB_NAME=garant
DB_USERNAME=garant_user
DB_PASSWORD=           # силна парола, мин. 20 символа

# JWT
JWT_SECRET=            # виж командата по-долу
JWT_EXPIRATION=604800000

# Начален администратор
ADMIN_NAME=Administrator
ADMIN_EMAIL=           # твой имейл
ADMIN_PASSWORD=        # силна парола

# CORS
CORS_ALLOWED_ORIGINS=https://52.58.114.218.sslip.io

# Web Push VAPID (от Стъпка 10)
VAPID_PUBLIC_KEY=      # Public Key
VAPID_PRIVATE_KEY=     # Private Key
VAPID_SUBJECT=mailto:твоят@имейл.com
```

Генерирай JWT secret:
```bash
openssl rand -base64 64
```

---

## Стъпка 12 — Копирай SSL сертификатите

```bash
mkdir /opt/attendtrack/certs

cp /etc/letsencrypt/live/52.58.114.218.sslip.io/fullchain.pem /opt/attendtrack/certs/
cp /etc/letsencrypt/live/52.58.114.218.sslip.io/privkey.pem  /opt/attendtrack/certs/
```

---

## Стъпка 13 — Стартирай приложението

> `nginx.conf` вече има правилния `server_name` — не се налага промяна.

```bash
cd /opt/attendtrack
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

`pull` сваля образите от Docker Hub. Няма нужда от локален build на EC2.

Провери статуса:
```bash
docker compose -f docker-compose.prod.yml ps
```

Очакван резултат:
```
NAME                STATUS
garant-postgres     running
garant-backend      running
garant-frontend     running
garant-nginx        running
```

Приложението е достъпно на: **https://52.58.114.218.sslip.io**

---

## Стъпка 14 — Автоматично подновяване на сертификата

```bash
crontab -e
```

Добави реда:

```
0 3 * * * certbot renew --quiet && \
  cp /etc/letsencrypt/live/52.58.114.218.sslip.io/fullchain.pem /opt/attendtrack/certs/ && \
  cp /etc/letsencrypt/live/52.58.114.218.sslip.io/privkey.pem /opt/attendtrack/certs/ && \
  docker compose -f /opt/attendtrack/docker-compose.prod.yml restart nginx
```

Тествай:
```bash
certbot renew --dry-run
```

---

## Полезни команди

```bash
# Статус на услугите
docker compose -f docker-compose.prod.yml ps

# Логове в реално време
docker compose -f docker-compose.prod.yml logs -f

# Логове на конкретна услуга
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend
docker compose -f docker-compose.prod.yml logs -f nginx

# Рестартирай
docker compose -f docker-compose.prod.yml restart

# Спри (данните се запазват)
docker compose -f docker-compose.prod.yml down

# Спри и изтрий базата (ВНИМАНИЕ: губиш данните!)
docker compose -f docker-compose.prod.yml down -v

# Свали и рестартирай само frontend (след нов push в Docker Hub)
docker compose -f docker-compose.prod.yml pull frontend
docker compose -f docker-compose.prod.yml up -d frontend

# Свали и рестартирай само backend
docker compose -f docker-compose.prod.yml pull backend
docker compose -f docker-compose.prod.yml up -d backend

# Влез в PostgreSQL
docker compose -f docker-compose.prod.yml exec postgres psql -U garant_user -d garant
```

---

## Качване на нова версия

**От локалната машина** — билдни и публикувай новите образи:

```bash
cd /path/to/AttendTrack

docker build -t <DOCKERHUB_USERNAME>/attendtrack-backend:latest .
docker build -t <DOCKERHUB_USERNAME>/attendtrack-frontend:latest ./frontend

docker push <DOCKERHUB_USERNAME>/attendtrack-backend:latest
docker push <DOCKERHUB_USERNAME>/attendtrack-frontend:latest
```

**На EC2** — свали и рестартирай:

```bash
cd /opt/attendtrack
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

---

## Отстраняване на проблеми

**Няма памет при Docker build:**
```bash
# Добави 2GB swap
dd if=/dev/zero of=/swapfile bs=128M count=16
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
# За да е постоянен след рестарт:
echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
```

**Backend не стартира:**
```bash
docker compose -f docker-compose.prod.yml logs backend
# Чести причини: грешна DB парола, липсващ .env.prod
```

**Nginx грешка 502:**
```bash
docker compose -f docker-compose.prod.yml logs nginx
# Frontend може да стартира бавно — изчакай 1-2 мин
```

**SSL грешка:**
```bash
ls -la /opt/attendtrack/certs/
# Трябва да има fullchain.pem и privkey.pem
```

**Certbot грешка (порт 80 зает):**
```bash
# Спри nginx контейнера преди certbot renew
docker compose -f docker-compose.prod.yml stop nginx
certbot renew
docker compose -f docker-compose.prod.yml start nginx
```
