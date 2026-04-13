import React, { forwardRef } from 'react';
import { Link } from 'react-router-dom';

export interface ButtonProps {
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
  variant?: 'primary' | 'secondary';
  disabled?: boolean;
  asChild?: boolean;
  type?: 'button' | 'submit' | 'reset';
}

const ButtonComp = forwardRef<HTMLButtonElement, ButtonProps>(({
  children,
  onClick,
  className = '',
  variant = 'primary',
  disabled = false,
  type = 'button',
}, ref) => {
  return (
    <button 
      ref={ref}
      type={type}
      disabled={disabled}
      onClick={onClick}
      className={`btn btn-${variant} ${className}`}
    >
      {children}
    </button>
  );
});

interface ButtonLinkProps {
  children: React.ReactNode;
  to: string;
  className?: string;
  variant?: 'primary' | 'secondary';
}

const ButtonLink = ({ children, to, className = '', variant = 'primary' }: ButtonLinkProps) => {
  return (
    <Link 
      to={to} 
      className={`btn btn-${variant} ${className}`}
    >
      {children}
    </Link>
  );
};

export const Button = Object.assign(ButtonComp, { Link: ButtonLink });

ButtonComp.displayName = 'Button';



