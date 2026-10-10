<script setup>
import { ref } from 'vue';

const contact = {
  founder: 'NGUYỄN THỊ TUYẾT NHI',
  email: 'CARROT686868@GMAIL.COM',
  phone: '0397446844',
  address: 'TP. Cần Thơ, Quận Ninh Kiều',
};
const mapQuery = encodeURIComponent('Ninh Kiều, Cần Thơ, Việt Nam');
const mapUrl = `https://www.google.com/maps?q=${mapQuery}&output=embed`;
const directionsUrl = `https://www.google.com/maps/search/?api=1&query=${mapQuery}`;
const mapLoaded = ref(false);
const faqOpen = ref(0);
const faqs = [
  {
    question: 'Mình muốn đặt sân, bắt đầu từ đâu?',
    answer:
      'Vào Lịch trống / Đặt sân, chọn loại sân phù hợp, ngày và khung giờ bạn muốn chơi. Hệ thống hiển thị các lựa chọn theo lịch và quy định đặt sân.',
  },
  {
    question: 'Chưa biết chọn loại sân nào thì sao?',
    answer:
      'Bạn có thể xem ưu điểm và bảng giá của Premium, Gold, Basic hoặc Daily Visitor. Nếu cần tư vấn thêm, gọi Carrot để chọn trải nghiệm phù hợp với nhóm của bạn.',
  },
  {
    question: 'Cần hỗ trợ booking đang có?',
    answer:
      'Chuẩn bị mã booking, số điện thoại đã đặt và thời gian chơi. Liên hệ hotline hoặc email để nhân viên kiểm tra và hỗ trợ theo chính sách của sân.',
  },
];
</script>

<template>
  <main class="contact-page">
    <header class="contact-hero">
      <div class="hero-orbit" aria-hidden="true"></div>
      <div class="hero-copy">
        <p class="eyebrow"><span></span> CARROT BADMINTON / LIÊN HỆ</p>
        <h1>Một cuộc gọi.<br />Một <em>trận cầu hay.</em></h1>
        <p class="hero-description">
          Bạn mang theo đam mê. Carrot giúp bạn tìm một lịch chơi phù hợp.<br
            class="desktop-break"
          />
          Hẹn gặp bạn trên sân, giữa lòng Cần Thơ.
        </p>
        <div class="hero-actions">
          <a :href="`tel:${contact.phone}`" class="button orange"
            >Gọi Carrot <span aria-hidden="true">↗</span></a
          >
          <a class="button outline" href="#booking">
            Khám phá lịch trống <span aria-hidden="true">→</span>
          </a>
        </div>
        <div class="hero-location">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z" />
            <circle cx="12" cy="10" r="2.5" />
          </svg>
          NINH KIỀU · CẦN THƠ
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="art-label">MEET YOU<br /><strong>ON COURT.</strong></div>
        <div class="court-stage">
          <div class="court">
            <i class="court-inner"></i><i class="court-middle"></i
            ><i class="court-service top"></i
            ><i class="court-service bottom"></i><i class="court-center"></i>
          </div>
          <span class="court-ball"></span>
        </div>
        <div class="art-caption">
          <span>01 / CONNECT</span><span>LET’S PLAY ↗</span>
        </div>
      </div>
    </header>

    <section class="contact-body" aria-labelledby="contact-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow light">KẾT NỐI VỚI CARROT</p>
          <h2 id="contact-title">
            Gần bạn hơn.<br /><span>Sẵn sàng cho trận tiếp theo.</span>
          </h2>
        </div>
        <p>
          Tư vấn chọn sân, hỗ trợ đặt lịch hoặc chia sẻ trải nghiệm.<br />Chọn
          cách liên hệ thuận tiện nhất với bạn.
        </p>
      </div>
      <div class="contact-grid">
        <div class="contact-details">
          <article class="founder-card">
            <span class="founder-avatar" aria-hidden="true">TN</span>
            <div>
              <p>NGƯỜI SÁNG LẬP</p>
              <h3>{{ contact.founder }}</h3>
              <span>Carrot Badminton</span>
            </div>
            <span class="founder-star" aria-hidden="true">✳</span>
          </article>
          <a :href="`tel:${contact.phone}`" class="contact-card"
            ><span class="icon-box"
              ><svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="m7 3 3 5-3 3c2 3 3 4 6 6l3-3 5 3c-1 5-5 5-9 3C6 17 1 10 3 6c1-2 2-3 4-3Z"
                /></svg
            ></span>
            <div>
              <p>HOTLINE / HỖ TRỢ ĐẶT SÂN</p>
              <strong>{{ contact.phone }}</strong
              ><small>Chạm để gọi trực tiếp</small>
            </div>
            <span class="card-arrow" aria-hidden="true">↗</span></a
          >
          <a :href="`mailto:${contact.email}`" class="contact-card email-card"
            ><span class="icon-box"
              ><svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="5" width="18" height="14" rx="3" />
                <path d="m3 6 9 7 9-7" /></svg
            ></span>
            <div>
              <p>EMAIL LIÊN HỆ</p>
              <strong>{{ contact.email }}</strong
              ><small>Gửi câu hỏi hoặc góp ý cho Carrot</small>
            </div>
            <span class="card-arrow" aria-hidden="true">↗</span></a
          >
          <a
            :href="directionsUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="contact-card"
            ><span class="icon-box"
              ><svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z" />
                <circle cx="12" cy="10" r="2.5" /></svg
            ></span>
            <div>
              <p>ĐỊA CHỈ</p>
              <strong>{{ contact.address }}</strong
              ><small>Mở khu vực trên Google Maps</small>
            </div>
            <span class="card-arrow" aria-hidden="true">↗</span></a
          >
          <div class="help-note">
            <span aria-hidden="true">✦</span>
            <p>
              Đã có lịch chơi? Khi liên hệ, gửi kèm
              <strong>mã booking và số điện thoại</strong> để Carrot hỗ trợ bạn
              dễ dàng hơn.
            </p>
          </div>
        </div>
        <article class="map-card">
          <header>
            <div>
              <p>FIND US HERE</p>
              <h3>Hẹn nhau tại Cần Thơ.</h3>
            </div>
            <span class="map-tag">NINH KIỀU</span>
          </header>
          <div class="map-frame">
            <div v-if="!mapLoaded" class="map-loading" role="status">
              Đang tải bản đồ…
            </div>
            <iframe
              :src="mapUrl"
              title="Bản đồ khu vực Ninh Kiều, Cần Thơ"
              loading="lazy"
              referrerpolicy="no-referrer-when-downgrade"
              allowfullscreen
              @load="mapLoaded = true"
            ></iframe>
          </div>
          <footer>
            <div>
              <strong>{{ contact.address }}</strong>
              <p>Gọi Carrot để được hướng dẫn vị trí sân chi tiết.</p>
            </div>
            <a
              :href="directionsUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="map-link"
              >Mở bản đồ <span aria-hidden="true">↗</span></a
            >
          </footer>
        </article>
      </div>
    </section>

    <section class="faq-section" aria-labelledby="faq-title">
      <div>
        <p class="eyebrow light">TRƯỚC KHI RA SÂN</p>
        <h2 id="faq-title">Bạn hỏi.<br />Carrot trả lời.</h2>
        <p>Một vài điều nhỏ để bạn bắt đầu dễ hơn.</p>
      </div>
      <div class="faq-list">
        <article
          v-for="(faq, index) in faqs"
          :key="faq.question"
          :class="{ expanded: faqOpen === index }"
        >
          <h3>
            <button
              :aria-expanded="faqOpen === index"
              :aria-controls="`contact-faq-${index}`"
              @click="faqOpen = faqOpen === index ? null : index"
            >
              <span
                ><small>0{{ index + 1 }}</small
                >{{ faq.question }}</span
              ><b aria-hidden="true">{{ faqOpen === index ? '−' : '+' }}</b>
            </button>
          </h3>
          <p v-show="faqOpen === index" :id="`contact-faq-${index}`">
            {{ faq.answer }}
          </p>
        </article>
      </div>
    </section>
    <section class="closing-banner">
      <div>
        <p>YOUR NEXT GAME STARTS HERE</p>
        <h2>Lịch chơi đẹp. Trận cầu vui.</h2>
      </div>
      <a class="button dark" href="#booking">
        Tìm lịch chơi của bạn <span aria-hidden="true">↗</span>
      </a>
    </section>
  </main>
</template>

<style scoped>
.contact-page {
  --ink: #26231f;
  --muted: #7e766b;
  --orange: #f28630;
  width: min(1280px, 100%);
  margin: auto;
  padding: 30px 24px 70px;
  color: var(--ink);
}
.contact-page *,
.contact-page *::before,
.contact-page *::after {
  box-sizing: border-box;
}
.contact-hero {
  position: relative;
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
  isolation: isolate;
  padding: 64px 56px 40px;
  border-radius: 28px;
  background: #201e1a;
  color: white;
  overflow: hidden;
}
.hero-orbit {
  position: absolute;
  width: 660px;
  height: 660px;
  right: -180px;
  top: -260px;
  border: 1px solid #d9994b22;
  border-radius: 50%;
  box-shadow:
    0 0 0 80px #e9b57308,
    0 0 0 160px #e9b57305;
  z-index: -1;
}
.eyebrow {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 2px;
  margin: 0 0 24px;
  color: #d9b187;
}
.eyebrow > span {
  width: 7px;
  height: 7px;
  background: var(--orange);
  border-radius: 50%;
  box-shadow: 0 0 16px #f2863070;
}
.hero-copy h1 {
  font-size: clamp(36px, 4.7vw, 64px);
  line-height: 1.13;
  letter-spacing: -2.5px;
  margin: 0;
  font-weight: 850;
}
.hero-copy em {
  color: #ffb66d;
  font-style: normal;
}
.hero-description {
  font-size: 14px;
  line-height: 1.9;
  color: #bfb8ad;
  margin: 24px 0 28px;
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  min-height: 48px;
  padding: 14px 20px;
  border-radius: 10px;
  border: 1px solid transparent;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  cursor: pointer;
  transition:
    transform 0.2s,
    background 0.2s;
}
.button:hover {
  transform: translateY(-3px);
}
.orange {
  background: var(--orange);
  color: #23190e;
}
.orange:hover {
  background: #ffae65;
}
.outline {
  border-color: #ffffff30;
  background: #ffffff06;
  color: #fff;
}
.outline:hover {
  background: #ffffff12;
}
.dark {
  background: #24211e;
  color: #fff;
}
.hero-location {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 40px;
  color: #aaa193;
  font-size: 10px;
  letter-spacing: 1.7px;
}
.hero-location svg {
  width: 16px;
  height: 16px;
}
svg {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.hero-art {
  position: relative;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 6px 0 0 32px;
}
.art-label {
  font-size: 12px;
  letter-spacing: 3px;
  color: #b8a386;
}
.art-label strong {
  display: block;
  margin-top: 6px;
  font-size: 27px;
  letter-spacing: 1px;
  color: #f4dcc0;
}
.court-stage {
  position: absolute;
  width: 100%;
  height: 260px;
  bottom: 35px;
  left: 0;
  perspective: 700px;
}
.court {
  position: absolute;
  width: 220px;
  height: 300px;
  left: 50%;
  top: -32px;
  margin-left: -110px;
  border: 2px solid #d8a562;
  background: linear-gradient(120deg, #b7844225, #4f3c2510);
  transform: rotateX(54deg) rotateZ(-25deg);
  box-shadow:
    18px 26px 0 #c3893e0a,
    30px 42px 40px #0005,
    inset 0 0 45px #d8a56210;
  animation: court-float 7s ease-in-out infinite;
}
.court i {
  position: absolute;
  display: block;
}
.court-inner {
  inset: 0 24px;
  border-inline: 1px solid #d8a56290;
}
.court-middle {
  top: 50%;
  left: 0;
  right: 0;
  border-top: 3px solid #ffe0ab;
  box-shadow: 0 0 18px #ffbf6360;
}
.court-service {
  left: 0;
  right: 0;
  border-top: 1px solid #d8a562;
}
.court-service.top {
  top: 32%;
}
.court-service.bottom {
  bottom: 32%;
}
.court-center {
  top: 0;
  bottom: 0;
  left: 50%;
  border-left: 1px solid #d8a56280;
}
.court-ball {
  position: absolute;
  width: 12px;
  height: 12px;
  left: 58%;
  top: 72px;
  background: #fff4d8;
  border-radius: 50%;
  box-shadow: 0 0 24px #ffc566;
}
.art-caption {
  display: flex;
  justify-content: space-between;
  font-size: 9px;
  letter-spacing: 1.5px;
  color: #b29e83;
  z-index: 1;
}
.contact-body {
  padding: 58px 8px 0;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 30px;
  margin-bottom: 30px;
}
.light {
  color: #a9753e;
  margin-bottom: 15px;
}
.section-heading h2,
.faq-section h2 {
  font-size: clamp(27px, 3vw, 38px);
  line-height: 1.25;
  letter-spacing: -1.2px;
  margin: 0;
}
.section-heading h2 span {
  color: #948779;
}
.section-heading > p {
  font-size: 12px;
  line-height: 1.9;
  color: var(--muted);
  margin: 0;
}
.contact-grid {
  display: grid;
  grid-template-columns: 1fr 1.15fr;
  gap: 28px;
  align-items: stretch;
}
.contact-details {
  min-width: 0;
  display: grid;
  gap: 12px;
}
.founder-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 22px;
  border: 1px solid #e8dfd1;
  border-radius: 16px;
  background: #f8f4ed;
}
.founder-avatar {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  background: #2e2923;
  color: #e6be8b;
  font-size: 18px;
  font-weight: 800;
  border-radius: 50%;
}
.founder-card p,
.contact-card p {
  font-size: 9px;
  letter-spacing: 1.3px;
  color: #9a7955;
  font-weight: 800;
  margin: 0 0 9px;
}
.founder-card h3 {
  font-size: 15px;
  line-height: 1.5;
  margin: 0 0 4px;
}
.founder-card div > span {
  font-size: 11px;
  color: var(--muted);
}
.founder-star {
  margin-left: auto;
  color: #bf8a49;
  font-size: 30px;
}
.contact-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 21px;
  border: 1px solid #eae5dc;
  border-radius: 16px;
  text-decoration: none;
  color: inherit;
  background: #fff;
  transition:
    border-color 0.2s,
    transform 0.2s,
    box-shadow 0.2s;
}
.contact-card:hover {
  border-color: #d8ac76;
  transform: translateX(4px);
  box-shadow: 0 8px 26px #6d49270a;
}
.icon-box {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  background: #fbf3e7;
  border-radius: 12px;
  color: #a2713d;
}
.icon-box svg {
  width: 22px;
  height: 22px;
}
.contact-card div {
  min-width: 0;
}
.contact-card strong {
  font-size: 17px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.contact-card small {
  display: block;
  color: var(--muted);
  font-size: 11px;
  margin-top: 6px;
}
.email-card strong {
  font-size: 14px;
}
.card-arrow {
  margin-left: auto;
  color: #b7a185;
  font-size: 22px;
}
.help-note {
  display: flex;
  gap: 12px;
  align-items: start;
  padding: 8px 4px;
}
.help-note > span {
  color: #b98247;
  font-size: 22px;
}
.help-note p {
  font-size: 12px;
  line-height: 1.8;
  color: var(--muted);
  margin: 0;
}
.map-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid #e7dfd4;
  border-radius: 20px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 12px 36px #48331d07;
}
.map-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 24px;
}
.map-card header p {
  margin: 0 0 9px;
  font-size: 9px;
  letter-spacing: 1.5px;
  color: #a2794e;
  font-weight: 800;
}
.map-card h3 {
  margin: 0;
  font-size: 22px;
  letter-spacing: -0.6px;
}
.map-tag {
  padding: 8px 10px;
  background: #f7f2e9;
  border: 1px solid #e7dccb;
  border-radius: 30px;
  font-size: 9px;
  font-weight: 800;
  color: #94704b;
  white-space: nowrap;
}
.map-frame {
  position: relative;
  flex: 1;
  min-height: 345px;
  background: #f4f1e9;
}
.map-frame iframe {
  display: block;
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 345px;
  border: 0;
}
.map-loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 12px;
}
.map-card footer {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
  padding: 22px 24px;
}
.map-card footer strong {
  font-size: 12px;
}
.map-card footer p {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.7;
  margin: 6px 0 0;
}
.map-link {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 800;
  color: #9b672f;
  white-space: nowrap;
  text-decoration: none;
}
.faq-section {
  display: grid;
  grid-template-columns: 0.8fr 1.2fr;
  gap: 56px;
  margin: 70px 8px 50px;
}
.faq-section > div > p:last-child {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.faq-list article {
  border-bottom: 1px solid #e7e0d6;
}
.faq-list h3 {
  margin: 0;
}
.faq-list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  min-height: 65px;
  padding: 16px 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.faq-list button span {
  display: flex;
  gap: 15px;
  align-items: center;
}
.faq-list small {
  color: #b58a56;
  font-size: 10px;
}
.faq-list b {
  font-size: 24px;
  font-weight: 400;
  color: #b68b56;
}
.faq-list article > p {
  padding: 0 26px 20px;
  margin: 0;
  font-size: 12px;
  line-height: 1.9;
  color: var(--muted);
}
.faq-list article.expanded button {
  color: #9c652e;
}
.closing-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 34px 40px;
  border-radius: 20px;
  background: linear-gradient(110deg, #f8e8d2, #f7f2e9);
}
.closing-banner p {
  margin: 0 0 10px;
  color: #a0784e;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.6px;
}
.closing-banner h2 {
  font-size: clamp(23px, 2.8vw, 32px);
  margin: 0;
  letter-spacing: -0.8px;
}
a:focus-visible,
button:focus-visible {
  outline: 3px solid #f28630;
  outline-offset: 4px;
}
@keyframes court-float {
  0%,
  100% {
    transform: rotateX(54deg) rotateZ(-25deg) translateY(0);
  }
  50% {
    transform: rotateX(54deg) rotateZ(-25deg) translateY(-10px);
  }
}
@media (max-width: 1000px) {
  .contact-hero {
    padding: 44px 32px;
  }
  .hero-art {
    padding-left: 8px;
  }
  .section-heading {
    align-items: start;
  }
  .contact-grid {
    gap: 18px;
  }
  .contact-card {
    padding: 18px;
  }
  .founder-star {
    display: none;
  }
  .map-card header,
  .map-card footer {
    padding: 20px;
  }
  .map-card h3 {
    font-size: 19px;
  }
  .map-card footer {
    flex-wrap: wrap;
  }
}
@media (max-width: 760px) {
  .contact-page {
    padding: 20px 16px 45px;
  }
  .contact-hero {
    grid-template-columns: 1fr;
    padding: 36px 26px;
    border-radius: 22px;
  }
  .hero-art {
    min-height: 240px;
    margin-top: 16px;
  }
  .court-stage {
    height: 180px;
    bottom: 20px;
  }
  .court {
    width: 170px;
    height: 220px;
    margin-left: -85px;
    top: 0;
  }
  .art-label {
    font-size: 10px;
  }
  .art-label strong {
    font-size: 20px;
  }
  .hero-copy h1 {
    letter-spacing: -1.5px;
  }
  .hero-description {
    font-size: 13px;
  }
  .hero-location {
    margin-top: 24px;
  }
  .section-heading {
    flex-direction: column;
    gap: 16px;
  }
  .contact-body {
    padding: 38px 0 0;
  }
  .contact-grid {
    grid-template-columns: 1fr;
  }
  .faq-section {
    grid-template-columns: 1fr;
    gap: 18px;
    margin: 45px 0 35px;
  }
  .closing-banner {
    align-items: start;
    flex-direction: column;
    padding: 26px;
  }
  .map-frame,
  .map-frame iframe {
    min-height: 320px;
  }
  .desktop-break {
    display: none;
  }
}
@media (max-width: 400px) {
  .contact-hero {
    padding: 30px 20px;
  }
  .hero-copy h1 {
    font-size: 34px;
  }
  .hero-actions {
    flex-direction: column;
  }
  .contact-card {
    gap: 12px;
    padding: 16px;
  }
  .contact-card strong {
    font-size: 14px;
  }
  .email-card strong {
    font-size: 12px;
  }
  .founder-card {
    padding: 18px;
  }
  .founder-card h3 {
    font-size: 13px;
  }
  .map-tag {
    display: none;
  }
  .icon-box {
    width: 38px;
    height: 38px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .court {
    animation: none;
  }
  .button,
  .contact-card {
    transition: none;
  }
  .button:hover,
  .contact-card:hover {
    transform: none;
  }
}
</style>
