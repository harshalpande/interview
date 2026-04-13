import React, { useEffect, useRef, useState } from 'react';
import { Button } from './Button';
import './ShareUrlToggle.css';

interface ShareUrlToggleProps {
  token: string;
}

const ShareUrlToggle: React.FC<ShareUrlToggleProps> = ({ token }) => {
  const [showUrl, setShowUrl] = useState(false);
  const [copied, setCopied] = useState(false);
  const copyTimeoutRef = useRef<number | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const publicOrigin = (process.env.REACT_APP_PUBLIC_ORIGIN || window.location.origin).replace(/\/$/, '');
  const shareUrl = `${publicOrigin}/join/${token}`;

  useEffect(() => {
    setCopied(false);
    return () => {
      if (copyTimeoutRef.current) {
        window.clearTimeout(copyTimeoutRef.current);
        copyTimeoutRef.current = null;
      }
    };
  }, [token]);

  const handleCopy = async () => {
    const setCopiedWithReset = () => {
      setCopied(true);
      if (copyTimeoutRef.current) {
        window.clearTimeout(copyTimeoutRef.current);
      }
      copyTimeoutRef.current = window.setTimeout(() => {
        setCopied(false);
        copyTimeoutRef.current = null;
      }, 1500);
    };

    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopiedWithReset();
      return;
    } catch {
      // Fallback for older browsers / permissions issues
      const input = inputRef.current;
      if (input) {
        input.focus();
        input.select();
        const ok = document.execCommand?.('copy');
        if (ok) {
          setCopiedWithReset();
        }
      }
    }
  };

  return (
    <div className="share-url-container">
      <Button
        variant="secondary"
        className={showUrl ? '' : 'share-url-cta'}
        onClick={() => {
          setShowUrl(!showUrl);
          if (showUrl) {
            setCopied(false);
          }
        }}
      >
        {showUrl ? 'Hide' : 'Share'} URL
      </Button>
      {showUrl && (
        <div className="share-url-field">
          <input 
            className="share-url-input"
            type="text" 
            value={shareUrl} 
            readOnly 
            ref={inputRef}
            onClick={(e) => (e.target as HTMLInputElement).select()}
          />
          <Button variant="secondary" onClick={handleCopy} className={copied ? 'copy-success' : ''}>
            {copied ? 'Copied' : 'Copy'}
          </Button>
        </div>
      )}
    </div>
  );
};

export default ShareUrlToggle;

