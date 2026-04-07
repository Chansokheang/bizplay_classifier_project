import { Poppins } from "next/font/google";
import "./globals.css";

const sans = Poppins({
  variable: "--font-poppins",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
});

export const metadata = {
  title: "BizPlay Classifier",
  description: "Intelligent expense classification for your business.",
};

import ProgressBarProvider from "../components/ProgressBarProvider";

export default function RootLayout({ children }) {
  return (
    <html lang="en" className={`${sans.variable} h-full antialiased`}>
      <body className="min-h-full">
        <ProgressBarProvider>
          {children}
        </ProgressBarProvider>
      </body>
    </html>
  );
}
