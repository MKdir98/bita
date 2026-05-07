#!/usr/bin/env python3
"""Merge chapter-01/02/03 into one markdown with unified bibliography (Persian numerals)."""
from pathlib import Path

FA = str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹")


def fa(n: int) -> str:
    return str(n).translate(FA)


# ch2 old citation number (1-based index in ch2's list) -> unified id (1-based)
CH2_MAP = {
    1: 9,
    2: 10,
    3: 11,
    4: 12,
    5: 1,
    6: 13,
    7: 5,
    8: 14,
    9: 2,
    10: 3,
    11: 15,
    12: 16,
    13: 17,
    14: 7,
    15: 8,
    16: 18,
    17: 19,
    18: 20,
    19: 21,
    20: 22,
    21: 23,
}


def renumber_ch2_citations(text: str) -> str:
    import re

    def repl(m):
        inner = m.group(1)
        parts = inner.replace("،", ",").split(",")
        out = []
        for p in parts:
            p = p.strip()
            if not p:
                continue
            # strip brackets if nested
            old = int(p.translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹", "0123456789")))
            new = CH2_MAP.get(old, old)
            out.append(fa(new))
        return "[" + "،".join(out) + "]"

    # Match [۱] or [۱،۲] etc.
    return re.sub(r"\[([۰-۹]+(?:،[۰-۹]+)*)\]", repl, text)


def strip_bibliography_from_ch2(text: str) -> str:
    if "## منابع" in text:
        return text.split("## منابع")[0].rstrip()
    return text


def strip_bibliography_from_ch3(text: str) -> str:
    if "## منابع" in text:
        return text.split("## منابع")[0].rstrip()
    return text


def fix_ch3_crossrefs(text: str) -> str:
    text = text.replace("[فصل ۲، مراجع ۹،۱۲،۱۵]", f"[{fa(2)}،{fa(16)}،{fa(8)}]")
    text = text.replace("[فصل ۲]", "فصل دوم")
    text = text.replace("[فصل ۲، بخش ۲.۵]", "فصل دوم (§۲.۵)")
    text = text.replace("[فصل ۲، مرجع ۱]", f"[{fa(9)}]")
    text = text.replace("[مرجع ۶]", f"[{fa(13)}]")
    text = text.replace("[فصل ۲، ۱۴–۱۵]", f"[{fa(7)}–{fa(8)}]")
    text = text.replace("[۹–۱۳]", f"[{fa(2)}–{fa(17)}]")
    text = text.replace("[۱۴–۱۸]", f"[{fa(7)}–{fa(20)}]")
    text = text.replace("[۱،۶،۸]", f"[{fa(9)}،{fa(13)}،{fa(14)}]")
    return text


BIBLIO = r"""
## منابع یکپارچه

[۱] V. S. De Castro et al., «Research trends in enterprise service bus (ESB) applications: A systematic mapping study,» *IEEE Access*, vol. ۷، ۲۰۱۹. DOI: 10.1109/ACCESS.2019.2962134

[۲] Q. Zhang et al., «A survey on large language models for software engineering,» *Science China Information Sciences*, ۲۰۲۵. DOI: 10.1007/s11432-025-4670-0

[۳] A. Fan et al., «Large language models for software engineering: Survey and open problems,» arXiv:2310.03533، ۲۰۲۳.

[۴] M. J. Jones, «Rethinking the ESB: Building a secure bus with an SOA gateway,» *Computer Fraud & Security*, ۲۰۱۲.

[۵] OASIS, «Web Services Security: SOAP Message Security 1.1,» OASIS Standard، ۲۰۰۶.

[۶] N. Gruschka et al., «Using WS-Security—Not as easy as it seems,» Technical Report، ۲۰۰۶.

[۷] A. Sharma and D. Spinellis, «Evaluating GitOps for large-scale cloud-native systems,» *IEEE Software*, vol. ۳۹، no. ۴، ۲۰۲۲.

[۸] R. Shrestha and A. A. N. Ali, «Configuration management in Kubernetes environments: A GitOps approach,» *Proc. IEEE/ACM UCC*، ۲۰۲۴.

[۹] N. A. Ochuba et al., «Systematic review of API gateway patterns for scalable and secure application architecture,» *Journal of Frontiers in Multidisciplinary Research*, vol. ۲، no. ۱، ۲۰۲۱.

[۱۰] A. Pereira-Vale et al., «Security in microservice-based systems: A multivocal literature review,» *Computers & Security*, vol. ۱۰۳، ۲۰۲۱.

[۱۱] A. Hannousse and S. Yahiouche, «Securing microservices and microservice architectures: A systematic mapping study,» *Computer Science Review*, vol. ۴۱، ۲۰۲۱.

[۱۲] Z. Lu et al., «A survey on microservices trust models for open systems,» *IEEE Access*, vol. ۱۱، ۲۰۲۳.

[۱۳] A. Chatterjee et al., «SFTSDH: Applying Spring Security Framework With TSD-Based OAuth2 to Protect Microservice Architecture APIs,» *IEEE Access*, vol. ۱۰، ۲۰۲۲.

[۱۴] M. Waseem et al., «Microservice security: a systematic literature review,» *PeerJ Computer Science*، ۲۰۲۳.

[۱۵] X. Hou et al., «Large language models for software engineering: A systematic literature review,» arXiv:2308.10620، ۲۰۲۳.

[۱۶] «A survey of using large language models for generating infrastructure as code,» arXiv:2404.00227، ۲۰۲۴.

[۱۷] J. Kannan, «Can LLMs configure software tools?» arXiv:2312.06121، ۲۰۲۳.

[۱۸] S. Kurrewar et al., «Streamlining Kubernetes deployments through GitOps methodologies,» *Proc. IEEE SCEECS*، ۲۰۲۵.

[۱۹] S. Thotakura et al., «Scalable CI/CD architecture using multi-fleet controllers and HAProxy for cluster management in Kubernetes,» *AIJCST*, vol. ۸، no. ۱، ۲۰۲۶.

[۲۰] A. Rahmatulloh et al., «Event-driven architecture to improve performance and scalability in microservices-based systems,» *Proc. IEEE ICADEIS*، ۲۰۲۲.

[۲۱] J. Ponge et al., «Analysing the performance and costs of reactive programming libraries in Java,» *Proc. ACM REBLS*، ۲۰۲۱. DOI: 10.1145/3486605.3486788

[۲۲] K. Mochniej and M. Badurowicz, «Performance comparison of microservices written using reactive and imperative approaches,» *Journal of Computer Sciences Institute*, vol. ۲۸، ۲۰۲۳.

[۲۳] S. Wang et al., «Machine/deep learning for software engineering: A systematic literature review,» *IEEE Transactions on Software Engineering*, vol. ۴۹، ۲۰۲۲.

[۲۴] مستند فنی سامانهٔ مرجع: `docs/01-legacy-system-overview.md` (مخزن پروژه BITA).
"""


def add_footnotes_intro(text: str) -> str:
    """Prepend YAML and short note on footnotes."""
    yaml = """---
title: "پایان‌نامه — فصول ۱ تا ۳ (یکپارچه)"
lang: fa-IR
dir: rtl
---

"""
    note = (
        "> **یادداشت ویرایشی:** متن اصلی به فارسی است. اصطلاحات فنی انگلیسی در پرانتز آمده‌اند؛ "
        "برای چند اختصار پرتکرار، پانویس‌های پایین‌صفحه به سبک pandoc (`[^برچسب]: توضیح`) اضافه شده‌اند.\n\n"
    )
    return yaml + note + text


def main():
    base = Path(__file__).parent
    ch1 = (base / "chapter-01-introduction.md").read_text(encoding="utf-8")
    ch2 = (base / "chapter-02-literature-review.md").read_text(encoding="utf-8")
    ch3 = (base / "chapter-03-methodology.md").read_text(encoding="utf-8")

    # Remove ch1 local references section
    if "## منابع" in ch1:
        ch1_body = ch1.split("## منابع")[0].rstrip()
    else:
        ch1_body = ch1

    ch2_body = strip_bibliography_from_ch2(ch2)
    ch2_body = renumber_ch2_citations(ch2_body)

    ch3_body = strip_bibliography_from_ch3(ch3)
    ch3_body = fix_ch3_crossrefs(ch3_body)

    # Footnotes for recurring English acronyms (first occurrence areas - inject after title)
    foot = (
        "\n\n[^ESB]: Enterprise Service Bus — گذرگاه سرویس سازمانی.\n"
        "[^SOA]: Service-Oriented Architecture — معماری سرویس‌گرا.\n"
        "[^LLM]: Large Language Model — مدل زبانی بزرگ.\n"
    )

    # Add footnote refs to first chapter heading paragraph lightly
    ch1_body = ch1_body.replace("(Enterprise Service Bus، ESB)", "(Enterprise Service Bus، ESB)[^ESB]")
    ch1_body = ch1_body.replace("(SOA)", "(SOA)[^SOA]")
    ch1_body = ch1_body.replace("(Large Language Models)", "(Large Language Models)[^LLM]")

    out = []
    out.append("# فصل اول تا سوم (سند یکپارچه)\n")
    out.append(ch1_body)
    out.append("\n\n---\n\n")
    out.append(ch2_body)
    out.append("\n\n---\n\n")
    out.append(ch3_body)
    out.append(BIBLIO)
    out.append(foot)

    merged = "\n".join(out)
    merged = add_footnotes_intro(merged)

    out_path = base / "thesis-chapters-1-3.md"
    out_path.write_text(merged, encoding="utf-8")
    print("Wrote", out_path)


if __name__ == "__main__":
    main()
