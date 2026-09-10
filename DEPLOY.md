# Деплой на AttendTrack — AWS EC2 (Amazon Linux) + Docker + HTTPS

**Стек:** Spring Boot (Java 21) · Next.js 16 · PostgreSQL 16 · Docker Compose · **Nginx (на хоста)**
**OS:** Amazon Linux 2023
**HTTPS:** Let's Encrypt (certbot)
**Домейн / URL:** https://tracker.garant-90.com

> **Архитектура на прод:** `docker-compose.prod.yml` вдига **три** контейнера — `postgres`, `backend`, `frontend` (frontend публикува порт `3000`). **Nginx НЕ е в Docker** — инсталиран е директно на хоста и реверс-проксира `https://tracker.garant-90.com` → `http://localhost:3000` чрез конфигурацията **`nginx.prod.conf`**.
>
> Файлът `nginx.conf` (с `proxy_pass http://frontend:3000` и домейн `*.sslip.io`) е за контейнеризиран nginx и **не се ползва** в текущия прод.

---

## Стъпка 1 — Създай EC2 инстанция

1. **AWS Console → EC2 → Launch Instance**
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

## Стъпка 2 — Elastic IP + DNS запис

Let's Encrypt изисква стабилен IP и валиден DNS запис за домейна.

1. AWS Console → **EC2 → Elastic IPs → Allocate Elastic IP address** → **Associate** към инстанцията.
2. В DNS доставчика на `garant-90.com` добави **A запис**: `tracker` → Elastic IP на сървъра.
3. Изчакай DNS-ът да се разпространи (`dig tracker.garant-90.com` трябва да връща IP-то).

---

## Стъпка 3 — Свържи се по SSH

```bash
chmod 400 /path/to/your-key.pem
ssh -i /path/to/your-key.pem ec2-user@<SERVER_IP>
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
dnf install -y docker
systemctl enable --now docker

# Docker Compose V2 (не е включен в пакета)
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

docker --version
docker compose version
```

---

## Стъпка 6 — Инсталирай Nginx (на хоста) и Certbot

```bash
dnf install -y nginx certbot
systemctl enable nginx
```

---

## Стъпка 7 — Вземи SSL сертификат

Спри nginx за момента, за да е свободен порт 80 за `--standalone`:

```bash
systemctl stop nginx
certbot certonly --standalone -d tracker.garant-90.com
```

Certbot ще поиска имейл за известия. Сертификатът е валиден **90 дни** (виж Стъпка 13 за подновяване).

Сертификатите се записват в:
```
/etc/letsencrypt/live/tracker.garant-90.com/fullchain.pem
/etc/letsencrypt/live/tracker.garant-90.com/privkey.pem
```

> `nginx.prod.conf` сочи директно към тези пътища — не се налага копиране на сертификати.

---

## Стъпка 8 — Билдни и публикувай образите в Docker Hub

Изпълни **от твоята локална машина**.

```bash
docker login
cd /path/to/AttendTrack

docker build -t ludogoriesoft/attendtrack-backend:latest .
docker build -t ludogoriesoft/attendtrack-frontend:latest ./frontend

docker push ludogoriesoft/attendtrack-backend:latest
docker push ludogoriesoft/attendtrack-frontend:latest
```

---

## Стъпка 9 — Качи конфигурацията на сървъра

Само конфигурационните файлове се качват — кодът идва от Docker Hub.

**От твоята локална машина:**

```bash
cd /path/to/AttendTrack

scp -i /path/to/your-key.pem docker-compose.prod.yml nginx.prod.conf .env.prod.example \
    ec2-user@<SERVER_IP>:/tmp/
```

**На EC2:**

```bash
mkdir -p /opt/attendtrack
mv /tmp/docker-compose.prod.yml /tmp/.env.prod.example /opt/attendtrack/
cd /opt/attendtrack
```

---

## Стъпка 10 — Конфигурирай Nginx (на хоста)

Постави `nginx.prod.conf` като конфигурация на хостовия nginx, тествай и стартирай:

```bash
mv /tmp/nginx.prod.conf /etc/nginx/nginx.conf
nginx -t
systemctl start nginx
```

> `nginx.prod.conf` вече подава `X-Real-IP` и `X-Forwarded-For` към приложението — това е **задължително**, за да се записва реалното IP на терминала в одита на присъствията. Ако промениш конфигурацията по-късно: `nginx -t && systemctl reload nginx`.

---

## Стъпка 11 — Генерирай VAPID ключове (Web Push)

```bash
dnf install -y nodejs npm
npx web-push generate-vapid-keys
```

Запиши изхода — нужен е за `.env`.

---

## Стъпка 12 — Създай .env

`docker-compose.prod.yml` чете променливите от файл **`.env`** (`env_file: .env`).

```bash
cd /opt/attendtrack
cp .env.prod.example .env
nano .env
```

Попълни всички стойности:

```env
# Database
DB_NAME=garant
DB_USERNAME=garant_user
DB_PASSWORD=            # силна парола, мин. 20 символа

# JWT
JWT_SECRET=            # openssl rand -base64 64
JWT_EXPIRATION=604800000

# Начален администратор
ADMIN_NAME=Administrator
ADMIN_EMAIL=           # твой имейл
ADMIN_PASSWORD=        # силна парола

# CORS — трябва да съвпада с домейна
CORS_ALLOWED_ORIGINS=https://tracker.garant-90.com

# Часова зона — ВАЖНО. Времената от терминалите са локални; ако сървърът върви на UTC,
# "Синхронизирано" изглежда преди "Записано", денят на сървъра се разминава с този на
# устройството, а авто-чекаутът тръгва в грешен местен час. По подразбиране Europe/Sofia.
APP_TIMEZONE=Europe/Sofia

# Web Push VAPID (от Стъпка 11)
VAPID_PUBLIC_KEY=
VAPID_PRIVATE_KEY=
VAPID_SUBJECT=mailto:твоят@имейл.com
```

---

## Стъпка 13 — Стартирай приложението

```bash
cd /opt/attendtrack
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

`pull` сваля образите от Docker Hub — няма нужда от локален build на EC2.

Провери статуса:
```bash
docker compose -f docker-compose.prod.yml ps
```

Очакван резултат (nginx е на хоста, НЕ е в списъка):
```
NAME                STATUS
garant-postgres     running
garant-backend      running
garant-frontend     running
```

Провери и хостовия nginx:
```bash
systemctl status nginx
```

Приложението е достъпно на: **https://tracker.garant-90.com**

---

## Стъпка 14 — Автоматично подновяване на сертификата

Flyway миграциите се пускат автоматично от backend-а при старт — тук се грижим само за сертификата.

```bash
crontab -e
```

Добави реда (подновява и презарежда хостовия nginx, за да поеме новия сертификат):

```
0 3 * * * certbot renew --quiet --deploy-hook "systemctl reload nginx"
```

Тествай:
```bash
certbot renew --dry-run
```

---

## Полезни команди

```bash
# Статус на контейнерите
docker compose -f docker-compose.prod.yml ps

# Логове в реално време
docker compose -f docker-compose.prod.yml logs -f
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend

# Nginx (на хоста)
systemctl status nginx
nginx -t && systemctl reload nginx
tail -f /var/log/nginx/error.log

# Рестартирай контейнерите
docker compose -f docker-compose.prod.yml restart

# Спри (данните се запазват)
docker compose -f docker-compose.prod.yml down

# Спри и изтрий базата (ВНИМАНИЕ: губиш данните!)
docker compose -f docker-compose.prod.yml down -v

# Свали и рестартирай само frontend / backend (след нов push)
docker compose -f docker-compose.prod.yml pull frontend && docker compose -f docker-compose.prod.yml up -d frontend
docker compose -f docker-compose.prod.yml pull backend  && docker compose -f docker-compose.prod.yml up -d backend

# Влез в PostgreSQL
docker compose -f docker-compose.prod.yml exec postgres psql -U garant_user -d garant
```

---

## Качване на нова версия

**От локалната машина** — билдни и публикувай новите образи:

```bash
cd /path/to/AttendTrack

docker build -t ludogoriesoft/attendtrack-backend:latest .
docker build -t ludogoriesoft/attendtrack-frontend:latest ./frontend

docker push ludogoriesoft/attendtrack-backend:latest
docker push ludogoriesoft/attendtrack-frontend:latest
```

**На EC2** — свали и рестартирай:

```bash
cd /opt/attendtrack
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

> Ако си променил `nginx.prod.conf`, качи го наново в `/etc/nginx/nginx.conf` и `nginx -t && systemctl reload nginx`.

---

## Отстраняване на проблеми

**Няма памет при Docker build:**
```bash
# Добави 2GB swap
dd if=/dev/zero of=/swapfile bs=128M count=16
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
```

**Backend не стартира:**
```bash
docker compose -f docker-compose.prod.yml logs backend
# Чести причини: грешна DB парола, липсващ .env
```

**Nginx грешка 502:**
```bash
tail -f /var/log/nginx/error.log
# Frontend може да стартира бавно — изчакай 1-2 мин. Провери: curl -I http://localhost:3000
```

**Реалното IP не се записва в одита:**
Провери, че `/etc/nginx/nginx.conf` (от `nginx.prod.conf`) съдържа
`proxy_set_header X-Real-IP $remote_addr;` и `X-Forwarded-For`, после `systemctl reload nginx`.

**SSL грешка:**
```bash
ls -la /etc/letsencrypt/live/tracker.garant-90.com/
# Трябва да има fullchain.pem и privkey.pem
```

**Certbot грешка (порт 80 зает):**
```bash
systemctl stop nginx
certbot renew
systemctl start nginx
```
