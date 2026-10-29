
<!DOCTYPE html>
<html lang="pl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="theme-color" content="#080b16">
  <title>Strona w budowie | GrupaGenZie</title>

  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    body {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      overflow: hidden;
      font-family: Inter, "Segoe UI", Arial, sans-serif;
      color: #f5f7ff;
      background:
        radial-gradient(ellipse at 50% 0%, #182652 0%, transparent 55%),
        #080b16;
    }

    .glow {
      position: fixed;
      width: 320px;
      height: 320px;
      border-radius: 50%;
      background: #526dff;
      filter: blur(150px);
      opacity: 0.18;
      pointer-events: none;
      animation: pulse 5s ease-in-out infinite alternate;
    }

    .container {
      position: relative;
      width: 100%;
      max-width: 650px;
      text-align: center;
      animation: appear 1s ease both;
    }

    .logo {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 65px;
      font-size: 18px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }

    .logo-icon {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border: 1px solid #607aff;
      border-radius: 12px;
      color: white;
      background: linear-gradient(135deg, #536dfe, #8b5cf6);
      box-shadow: 0 0 25px #536dfe40;
    }

    .status {
      display: inline-flex;
      align-items: center;
      gap: 9px;
      padding: 9px 15px;
      margin-bottom: 26px;
      border: 1px solid #ffffff18;
      border-radius: 50px;
      color: #b9c5ff;
      background: #ffffff08;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #6df7ae;
      box-shadow: 0 0 12px #6df7ae;
      animation: blink 1.5s infinite;
    }

    h1 {
      margin-bottom: 22px;
      font-size: clamp(42px, 8vw, 76px);
      line-height: 1.04;
      letter-spacing: -4px;
      font-weight: 850;
    }

    h1 span {
      color: #8495ff;
      background: linear-gradient(90deg, #91a3ff, #c5a5ff);
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .description {
      max-width: 460px;
      margin: 0 auto;
      color: #9ca8c4;
      font-size: 16px;
      line-height: 1.8;
    }

    .progress-card {
      max-width: 420px;
      margin: 42px auto 0;
      padding: 22px;
      text-align: left;
      border: 1px solid #ffffff12;
      border-radius: 18px;
      background: #ffffff06;
      backdrop-filter: blur(15px);
    }

    .progress-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 14px;
      font-size: 13px;
    }

    .progress-info span:first-child {
      color: #dce3ff;
      font-weight: 600;
    }

    .percentage {
      color: #a9b6ff;
      font-weight: 800;
    }

    .progress-bar {
      height: 6px;
      overflow: hidden;
      border-radius: 20px;
      background: #ffffff12;
    }

    .progress-fill {
      width: 65%;
      height: 100%;
      border-radius: inherit;
      background: linear-gradient(90deg, #6179ff, #b28aff);
      box-shadow: 0 0 15px #6179ff80;
      animation: load 1.8s ease-out both;
    }

    .note {
      margin-top: 14px;
      color: #77829d;
      font-size: 12px;
    }

    .footer {
      margin-top: 55px;
      color: #59647f;
      font-size: 12px;
    }

    .footer span {
      color: #8796ff;
    }

    @keyframes appear {
      from { opacity: 0; transform: translateY(20px); }
      to { opacity: 1; transform: translateY(0); }
    }

    @keyframes pulse {
      from { transform: scale(0.9); opacity: 0.12; }
      to { transform: scale(1.2); opacity: 0.24; }
    }

    @keyframes blink {
      50% { opacity: 0.4; }
    }

    @keyframes load {
      from { width: 0; }
      to { width: 65%; }
    }

    @media (max-width: 480px) {
      .logo { margin-bottom: 48px; }
      h1 { letter-spacing: -2px; }
      .description { font-size: 14px; }
      .progress-card { padding: 18px; }
      .footer { margin-top: 40px; }
    }

    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after {
        animation-duration: 0.01ms !important;
        animation-iteration-count: 1 !important;
      }
    }
  </style>
</head>

<body>
  <div class="glow"></div>

  <main class="container">
    <div class="logo">
      <div class="logo-icon">GZ</div>
      <span>GrupaGenZie</span>
    </div>

    <div class="status">
      <span class="dot"></span>
      Pracujemy nad stroną
    </div>

    <h1>Coś nowego<br><span>nadchodzi.</span></h1>

    <p class="description">
      Właśnie tworzymy coś wyjątkowego.
      Nasza strona jest obecnie w budowie,
      ale już niedługo wszystko będzie gotowe.
      Wpadnij do nas ponownie!
    </p>

    <section class="progress-card">
      <div class="progress-info">
        <span>Postęp prac</span>
        <span class="percentage">65%</span>
      </div>

      <div
        class="progress-bar"
        role="progressbar"
        aria-label="Postęp prac nad stroną"
        aria-valuenow="65"
        aria-valuemin="0"
        aria-valuemax="100"
      >
        <div class="progress-fill"></div>
      </div>

      <p class="note">Dopracowujemy ostatnie szczegóły ✨</p>
    </section>

    <footer class="footer">
      © <span id="year"></span> GrupaGenZie · Tworzymy coś fajnego.
    </footer>
  </main>

  <script>
    document.getElementById("year").textContent =
      new Date().getFullYear();
  </script>
</body>
</html>
