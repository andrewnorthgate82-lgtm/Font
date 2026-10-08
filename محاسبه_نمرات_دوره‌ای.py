# -*- coding: utf-8 -*-
"""
آینه فارسی ماژول محاسبه نمرات دوره‌ای نواحی نسرا
(Persian alias for calculate_period_scores.py)
"""
import sys
from calculate_period_scores import run_period_evaluation

if __name__ == '__main__':
    arg_m = 3
    arg_scale = '0-100'

    if len(sys.argv) > 1:
        raw_m = sys.argv[1].strip()
        if raw_m in ['2', '3', '6']:
            arg_m = int(raw_m)
        elif raw_m in ['0', 'real', '0-100']:
            arg_scale = '0-100'
        elif raw_m in ['70', 'nasra', '70-100']:
            arg_scale = '70-100'

    if len(sys.argv) > 2:
        raw_s = sys.argv[2].strip().lower()
        if raw_s in ['0', 'real', '0-100', '1']:
            arg_scale = '0-100'
        elif raw_s in ['70', 'nasra', '70-100', '2']:
            arg_scale = '70-100'

    if len(sys.argv) == 1:
        print("\n=======================================================")
        print("  سامانه ارزیابی عملکرد دوره‌ای نواحی نسرا (اصفهان)")
        print("=======================================================")
        print("لطفاً بازه زمانی ارزیابی را انتخاب فرمایید:")
        print("  [1] عملکرد ۲ ماهه")
        print("  [2] عملکرد ۳ ماهه (فصلی - پیش‌فرض)")
        print("  [3] عملکرد ۶ ماهه")
        
        try:
            choice = input("\nشماره گزینه (1 / 2 / 3) [پیش‌فرض 2]: ").strip()
            if choice == '1':
                arg_m = 2
            elif choice == '3':
                arg_m = 6
            else:
                arg_m = 3
        except (EOFError, KeyboardInterrupt):
            arg_m = 3

        print("\nلطفاً مقیاس نمره‌دهی مورد نظر را انتخاب فرمایید:")
        print("  [1] مقیاس واقعی (۰ تا ۱۰۰ - پیش‌فرض)")
        print("  [2] مقیاس استاندارد نسرا (۷۰ تا ۱۰۰)")
        
        try:
            choice_sc = input("\nشماره گزینه (1 / 2) [پیش‌فرض 1]: ").strip()
            if choice_sc == '2':
                arg_scale = '70-100'
            else:
                arg_scale = '0-100'
        except (EOFError, KeyboardInterrupt):
            arg_scale = '0-100'

    run_period_evaluation(selected_months=arg_m, selected_scale=arg_scale)
